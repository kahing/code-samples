#include "StringMatcher2.h"
#include <assert.h>
#include <algorithm>

void
StringMatcher2::add_exact_match(const char *exact_str, int id)
{
    std::string word(exact_str);
    auto lower = std::lower_bound(prefix_tree.begin(), prefix_tree.end(),
        word);
    if (lower != prefix_tree.end() && lower->word == word) {
        lower->exact_val = id;
    } else {
        auto res = prefix_tree.insert(lower, StringMatcher2::prefix_node());
        res->word = word;
        res->exact_val = id;
    }
}

void
StringMatcher2::add_prefix_match(const char *prefix_str, int id)
{
    std::string word(prefix_str);
    auto lower = std::lower_bound(prefix_tree.begin(), prefix_tree.end(),
        word);
    if (lower != prefix_tree.end() && lower->word == word) {
        lower->prefix_val = id;
    } else {
        auto res = prefix_tree.insert(lower, StringMatcher2::prefix_node());
        res->word = word;
        res->prefix_val = id;
    }
}

static std::string
common_prefix(const char *str1, const char *str2)
{
    const char *r1 = str1, *r2 = str2;
    for (; r1[0] == r2[0]; r1++, r2++);
    return std::string(str1, r1 - str1);
}

int
StringMatcher2::prefix_node::operator<(const StringMatcher2::prefix_node &other) const
{
    return word < other.word;
}

int
StringMatcher2::prefix_node::operator<(const std::string &other) const
{
    return word < other;
}

int
StringMatcher2::lookup(const char *input) const
{
    bool first = true;
    std::string lookfor(input);

    while (true) {
        auto lower_bound = std::lower_bound(prefix_tree.begin(),
            prefix_tree.end(), lookfor);

        if (lower_bound != prefix_tree.end()) {
            if (first) {
                if (lower_bound->word == lookfor) {
                    if (lower_bound->exact_val != -1)
                        return lower_bound->exact_val;
                    return lower_bound->prefix_val;
                }
            }

            if (lower_bound->word == lookfor) {
                if (lower_bound->prefix_val != -1)
                    return lower_bound->prefix_val;
            }

            if (lower_bound == prefix_tree.begin())
                return -1;
        }

        --lower_bound;
        std::string common = common_prefix(lookfor.c_str(),
            lower_bound->word.c_str());

        lookfor = common;
        first = false;
    }
}

bool
StringMatcher2::delete_exact_match(const char *exact_str)
{
    std::string word(exact_str);
    auto iter = std::lower_bound(prefix_tree.begin(), prefix_tree.end(),
        word);
    if (iter == prefix_tree.end()) return false;

    if (iter->word == word) {
        if (iter->exact_val != -1) {
            iter->exact_val = -1;

            if (iter->prefix_val == -1) {
                prefix_tree.erase(iter);
            }

            return true;
        }
    }

    return false;
}

bool
StringMatcher2::delete_prefix_match(const char *prefix_str)
{
    std::string word(prefix_str);
    auto iter = std::lower_bound(prefix_tree.begin(), prefix_tree.end(),
        word);
    if (iter == prefix_tree.end()) return false;

    if (iter->word == word) {
        if (iter->prefix_val != -1) {
            iter->prefix_val = -1;

            if (iter->exact_val == -1) {
                prefix_tree.erase(iter);
            }

            return true;
        }
    }

    return false;
}
