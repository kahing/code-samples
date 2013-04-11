#if 0
g++ -Wall -Werror --std=c++11 $0 -o $0.bin -g -lmysqlpp -lldns -lrt && exec $0.bin $@
exit $?
#else

/**
mysql> create table domains (
       rank INT NOT NULL AUTO_INCREMENT,
       name VARCHAR(255) NOT NULL,
       PRIMARY KEY (rank) );
mysql> insert into domains (name) values ("google.com");
...
mysql> select * from domains;
+------+---------------+
| rank | name          |
+------+---------------+
|    1 | google.com    |
|    2 | facebook.com  |
|    3 | youtube.com   |
|    4 | yahoo.com     |
|    5 | live.com      |
|    6 | wikipedia.com |
|    7 | baidu.com     |
|    8 | blogger.com   |
|    9 | msn.com       |
|   10 | gg.com        |
+------+---------------+

mysql> create table queries (
       name VARCHAR(255) PRIMARY KEY,
       avg FLOAT UNSIGNED,
       stddev FLOAT UNSIGNED,
       sum INT UNSIGNED,
       sum_sq BIGINT,
       num INT UNSIGNED,
       first TIMESTAMP default 0,
       last TIMESTAMP default 0);
 */

#define MYSQLPP_MYSQL_HEADERS_BURIED
#include <mysql++/mysql++.h>
#include <ldns/ldns.h>

#include <fstream>
#include <functional>
#include <iostream>
#include <list>
#include <memory>
#include <mutex>
#include <sstream>
#include <thread>

#include <math.h>
#include <time.h>
#include <unistd.h>

class DomainQuery {
public:
    DomainQuery(mysqlpp::Connection &conn, const std::string domain);
    ~DomainQuery();

    DomainQuery(const DomainQuery &) = delete;
    DomainQuery & operator=(const DomainQuery&) = delete;

    // loads from the db if possible
    void init(mysqlpp::Row &row);

    void query();

    float avg() const { return ((float)sum_) / ntimes_; }
    float stddev() const {
        float mean = avg();
        return sqrtf((float(sumsq_) / ntimes_) - (mean * mean));
    }

    uint32_t ntimes() const { return ntimes_; }
    uint32_t sum() const { return sum_; }
    uint64_t sumsq() const { return sumsq_; }
    const std::string &domain() const { return domain_; }
    time_t last_time() const { return last_time_; }

    std::string get_query_domain() const;

private:
    mysqlpp::Connection &conn_;
    std::string domain_;
    uint32_t sum_;
    uint64_t sumsq_;
    uint32_t ntimes_;
    time_t last_time_;

    ldns_resolver *resolver_;
};

DomainQuery::DomainQuery(mysqlpp::Connection &conn, const std::string domain) :
    conn_(conn), domain_(domain), sum_(0), sumsq_(0), ntimes_(0),
    last_time_(0), resolver_(NULL)
{
    ldns_status s = ldns_resolver_new_frm_file(&resolver_, NULL);
    if (s != LDNS_STATUS_OK) throw s;
}

DomainQuery::~DomainQuery()
{
    if (resolver_) {
        ldns_resolver_deep_free(resolver_);
    }
}

void
DomainQuery::init(mysqlpp::Row &row)
{
    sum_ = row["sum"];
    sumsq_ = row["sum_sq"];
    ntimes_ = row["num"];
}

std::string
DomainQuery::get_query_domain() const
{
    char buf[256];
    int i;
    for (i = 0; i < 8; i++) {
        buf[i] = random() % 26 + 'A';
    }

    buf[i++] = '.';
    buf[i] = '\0';

    return std::string(buf) + domain_;
}

void
DomainQuery::query()
{
    struct timespec start;
    clock_gettime(CLOCK_MONOTONIC_RAW, &start);

    std::string domain_to_query = get_query_domain();
    ldns_rdf *rdf = ldns_dname_new_frm_str(domain_to_query.c_str());
    if (rdf == NULL) {
        std::cerr << domain_to_query << " is not a valid domain" << std::endl;
        return;
    }

    ldns_pkt *p = ldns_resolver_query(resolver_, rdf, LDNS_RR_TYPE_A,
        LDNS_RR_CLASS_IN, LDNS_RD);
    if (p != NULL) {
        struct timespec end;
        clock_gettime(CLOCK_MONOTONIC_RAW, &end);

        uint32_t ms = (end.tv_sec - start.tv_sec) * 1000 +
            (end.tv_nsec - start.tv_nsec) / 1000000;
        sum_ += ms;
        sumsq_ += ms * ms;
        ++ntimes_;
        last_time_ = time(NULL);
        ldns_pkt_free(p);
    } else {
        std::cerr << domain_to_query << " query failed" << std::endl;
    }

    ldns_rdf_deep_free(rdf);
}

class ParallelQuery
{
public:
    ParallelQuery(const std::list< std::shared_ptr < DomainQuery > > &domains,
                  int threads);
    ~ParallelQuery();
    void run();

private:
    std::list< std::shared_ptr < DomainQuery > > domains_;
    std::vector< std::shared_ptr<std::thread> > threads_;
    std::mutex lock_;
};

ParallelQuery::ParallelQuery(const std::list< std::shared_ptr < DomainQuery > > &domains, int threads) :
    domains_(domains)
{
    for (int i = 0; i < threads; i++) {
        std::shared_ptr<std::thread> t(new std::thread(std::bind(&ParallelQuery::run, this)));
        threads_.push_back(t);
    }
}

ParallelQuery::~ParallelQuery()
{
    for (auto t : threads_) {
        t->join();
    }
}

void
ParallelQuery::run()
{
    while (true) {
        std::shared_ptr < DomainQuery > d;
        {
            std::lock_guard<std::mutex> _(lock_);

            if (domains_.empty()) {
                return;
            }
            d = domains_.front();
            domains_.pop_front();
        }

        d->query();
    }
}

struct mysql_options {
    std::string database;
    std::string user;
    std::string server;
    std::string password;
    std::string table;
};

class DnsQuery {
public:
    DnsQuery(const struct mysql_options &dbopt);
    ~DnsQuery();

    void init();

    void queryAll(int parallel);

private:
    bool init_;
    mysqlpp::Connection conn_;
    struct mysql_options dbopt_;
    std::list< std::shared_ptr<DomainQuery> > domains_;
};

DnsQuery::DnsQuery(const struct mysql_options &dbopt) :
    init_(false), dbopt_(dbopt)
{
}

DnsQuery::~DnsQuery()
{
    if (init_) {
        try {
            conn_.disconnect();
        } catch (...) {}
    }

    mysql_library_end();
}

void
DnsQuery::init()
{
    if (init_) return;

    conn_.connect(dbopt_.database.c_str(), dbopt_.server.c_str(),
        dbopt_.user.c_str(), dbopt_.password.c_str());

    init_ = true;

    try {
        // see if we can reuse the existing records
        mysqlpp::Query query = conn_.query();
        query << "select * from " << dbopt_.table;
        mysqlpp::UseQueryResult res = query.use();
        while (mysqlpp::Row row = res.fetch_row()) {
            std::string name(row["name"]);
            std::shared_ptr<DomainQuery> d(new DomainQuery(conn_, name));
            domains_.push_back(d);
            domains_.back()->init(row);
        }
    } catch (const std::exception &e) {
        // the table probably didnt' exist
        mysqlpp::Query query = conn_.query();
        query << "create table " << dbopt_.table << "("
            "name VARCHAR(255) PRIMARY KEY,"
            "avg FLOAT UNSIGNED,"
            "stddev FLOAT UNSIGNED,"
            "sum INT UNSIGNED,"
            "sum_sq BIGINT,"
            "num INT UNSIGNED,"
            "first TIMESTAMP default 0,"
            "last TIMESTAMP default 0);";
        query.execute();

        // load from list of domains to query
        query = conn_.query("select * from domains");
        mysqlpp::UseQueryResult res = query.use();
        while (mysqlpp::Row row = res.fetch_row()) {
            std::string name(row["name"]);
            std::shared_ptr<DomainQuery> d(new DomainQuery(conn_, name));
            domains_.push_back(d);
        }
    }

    if (domains_.empty()) {
        std::cerr << "no domains to query" << std::endl;
        exit(1);
    }
}

void
DnsQuery::queryAll(int parallel)
{
    {
        // query domains in parallel
        ParallelQuery(domains_, parallel);
    }

    for (auto d : domains_) {
        if (d->ntimes() == 0) {
            continue;
        }

        try {
            // update can probably be done in parallel as well, or
            // maybe done in one transaction with all the updates in
            // it
            mysqlpp::Query query = conn_.query();
            static char timebuf[sizeof("0000-00-00 00:00:00")];
            struct tm t;
            time_t query_time = d->last_time();
            localtime_r(&query_time, &t);
            strftime(timebuf, sizeof(timebuf), "%Y-%m-%d %T", &t);

            if (d->ntimes() > 1) {
                query << "update " << dbopt_.table << " set"
                      << " avg=" << d->avg()
                      << " ,stddev=" << d->stddev()
                      << " ,sum=" << d->sum()
                      << " ,sum_sq=" << d->sumsq()
                      << " ,num=" << d->ntimes()
                      << " ,last=" << mysqlpp::quote << timebuf
                      << " where name=" << mysqlpp::quote << d->domain();
            } else if (d->ntimes() == 1) {
                query << "insert into " << dbopt_.table << " values("
                      << mysqlpp::quote << d->domain()
                      << "," << d->avg()
                      << "," << d->stddev()
                      << "," << d->sum()
                      << "," << d->sumsq()
                      << "," << d->ntimes()
                      << "," << mysqlpp::quote << timebuf
                      << "," << mysqlpp::quote << timebuf
                      << ")";
            }

            query.execute();
        } catch (const std::exception &e) {
            std::cerr << e.what() << std::endl;
        }
    }
}

static void
strtoint(const std::string &s, int *out_val)
{
    std::stringstream ss(s);
    int val;

    ss >> val;
    if (!ss.fail()) {
        *out_val = val;
    }
}

static std::string
read_password(const std::string &file)
{
    std::ifstream ifs(file);
    std::string res;
    getline(ifs, res);
    ifs.close();
    return res;
}

static void
usage(const char *name)
{
    std::cerr << name
              << " -d <mysql database>"
              << " -u <mysql user>"
              << " -s <mysql server>"
              << " -p <passwd file>"
              << " -f <frequency in seconds>"
              << " -i <iterations, 0 for infinite>"
              << " -P <number of parallel queries>"
              << " -t <table to store result into>"
              << std::endl;
    exit(1);
}

int
main(int argc, char *argv[])
{
    int i = 1;
    int frequency = 10;
    int iterations = 0;
    int parallel = 10;
    struct mysql_options dbopt = {
        "thousandeyes", "root", "localhost", "", "queries"
    };
    std::string password_file = "passwd";

    for (; i < argc; i++) {
        if (argv[i][0] == '-') {
            char opt = argv[i][1];

            if (opt == '\0') break;

            if (++i < argc) {
                switch (opt) {
                // mysql options
                case 'd':
                    dbopt.database = argv[i];
                    break;
                case 'u':
                    dbopt.user = argv[i];
                    break;
                case 's':
                    dbopt.server = argv[i];
                    break;
                case 'p':
                    password_file = argv[i];
                    break;
                case 't':
                    dbopt.table = argv[i];
                    break;

                // program options
                case 'f':
                    strtoint(argv[i], &frequency);
                    break;
                case 'i':
                    strtoint(argv[i], &iterations);
                    break;
                case 'P':
                    strtoint(argv[i], &parallel);
                    break;
                default:
                    std::cerr << "unrecognized option " << opt << std::endl;
                    usage(argv[0]);
                };
            } else {
                usage(argv[0]);
            }
        }
    }

    std::cout << argv[0]
              << " -d " << dbopt.database
              << " -u " << dbopt.user
              << " -s " << dbopt.server
              << " -p " << password_file
              << " -f " << frequency
              << " -i " << iterations
              << " -P " << parallel
              << " -t " << dbopt.table
              << std::endl;

    dbopt.password = read_password(password_file);
    if (dbopt.password.empty()) {
        std::cerr << "password not provided" << std::endl;
        return 1;
    }

    DnsQuery query(dbopt);
    try {
        query.init();
    } catch (const std::exception &e) {
        std::cerr << "unable to connect to database: " << e.what()
                  << std::endl;
        return 1;
    }

    for (int i = 0; iterations == 0 || i < iterations; i++) {
        if (i != 0) {
            sleep(frequency);
        }
        query.queryAll(parallel ? parallel : 1);
    }

    return 0;
}

#endif
