#ifndef STRINGMATCHER2_H
#define STRINGMATCHER2_H

#include <vector>
#include <string>

class StringMatcher2
{
public:
    virtual void add_exact_match(const char *exact_str, int id);

    virtual void add_prefix_match(const char *prefix_str, int id);

    virtual int lookup(const char *input) const;

    virtual bool delete_exact_match(const char *exact_str);

    virtual bool delete_prefix_match(const char *prefix_str);

private:

    struct prefix_node
    {
        prefix_node() : prefix_val(-1), exact_val(-1) {}

        int operator<(const prefix_node &other) const;

        int operator<(const std::string &other) const;

        std::string word;
        int prefix_val;
        int exact_val;
    };

    std::vector< prefix_node >  prefix_tree;
};

#endif // STRINGMATCHER2_H
