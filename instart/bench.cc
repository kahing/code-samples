/**
 * $ time make run-bench
 * ./bench /usr/share/dict/american-english test 100000000
 * added 99154
 * found testingisfun 100000000
 * 
 * real    0m4.606s
 * user    0m4.532s
 * sys     0m0.068s
 *
 * memory usage ~78MB
 */

#include <fstream>
#include <iostream>
#include <string>
#include "StringMatcher.h"
#include <stdlib.h>

int
main(int argc, char **argv)
{
    if (argc < 4) exit(1);

    std::ifstream ifs(argv[1]);
    std::string s;
    StringMatcher matcher;
    int cnt = 0;

    while (ifs >> s) {
        for (size_t i = 0; i < s.size(); i++) {
            if (s[i] < 'A' || (s[i] > 'Z' && s[i] < 'a') || s[i] > 'z') {
                s.resize(i);
                break;
            }
        }

        if (!s.empty()) {
            cnt++;
            if (random() % 2 == 0) {
                matcher.add_exact_match(s.c_str(), 1);
            } else {
                matcher.add_prefix_match(s.c_str(), 2);
            }
        }
    }

    std::cout << "added " << cnt << std::endl;

    int ntimes = atoi(argv[3]);
    int val = 0;
    for (int i = 0; i < ntimes; i++) {
        val += matcher.lookup(argv[2]);
    }

    std::cout << "found " << argv[2] << " " << val << std::endl;

    return 0;
}
