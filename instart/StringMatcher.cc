#include "StringMatcher.h"
#include <stdlib.h>
#include <assert.h>

StringMatcher::prefix_node::prefix_node() :
    prefix_val(-1), exact_val(-1), children(new prefix_node *[52])
{}

int
StringMatcher::prefix_node::char_to_index(char c) const
{
    if (c >= 'A' && c <= 'Z') return c - 'A';
    else if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    assert(!"unexpected character");
}

StringMatcher::prefix_node *
StringMatcher::prefix_node::add(const char *prefix_str)
{
    if (*prefix_str == '\0') return NULL;

    int i = char_to_index(*prefix_str);
    if (children[i] == NULL) {
        children[i] = new prefix_node;
    }

    if (prefix_str[1]) {
        return children[i]->add(prefix_str + 1);
    } else {
        return children[i];
    }
}

bool
StringMatcher::prefix_node::del(const char *prefix_str, bool exact)
{
    if (*prefix_str == '\0') return false;

    int i = char_to_index(*prefix_str);

    if (children[i]) {
        if (prefix_str[1]) {
            return children[i]->del(prefix_str + 1, exact);
        } else {
            if (exact) {
                if (children[i]->exact_val != -1) {
                    children[i]->exact_val = -1;
                    return true;
                } else {
                    return false;
                }
            } else {
                if (children[i]->prefix_val != -1) {
                    children[i]->prefix_val = -1;
                    return true;
                } else {
                    return false;
                }
            }
        }
    } else {
        return false;
    }
}

bool
StringMatcher::prefix_node::compact()
{
    int emptyc = 0;
    for (int i = 0; i < 52; i++) {
        if (children[i]) {
            if (children[i]->prefix_val == -1 &&
                children[i]->exact_val == -1) {
                if (children[i]->compact()) {
                    delete children[i];
                    children[i] = NULL;
                    emptyc++;
                }
            }
        } else {
            emptyc++;
        }
    }

    return emptyc == 52;
}

int
StringMatcher::prefix_node::lookup(const char *prefix_str) const
{
    if (*prefix_str == '\0') return -1;

    int i = char_to_index(*prefix_str);

    if (children[i]) {
        int longer_match = children[i]->lookup(prefix_str + 1);
        if (longer_match != -1) return longer_match;
        if (prefix_str[1] == '\0' && children[i]->exact_val != -1)
            return children[i]->exact_val;
        return children[i]->prefix_val;
    } else {
        return -1;
    }
}

void
StringMatcher::add_exact_match(const char *exact_str, int id)
{
    assert(id > 0);
    assert(exact_str[0]);
    StringMatcher::prefix_node *n = prefix_tree.add(exact_str);
    n->exact_val = id;
}

void
StringMatcher::add_prefix_match(const char *prefix_str, int id)
{
    assert(id > 0);
    assert(prefix_str[0]);
    StringMatcher::prefix_node *n = prefix_tree.add(prefix_str);
    n->prefix_val = id;
}

int
StringMatcher::lookup(const char *input) const
{
    return prefix_tree.lookup(input);
}

bool
StringMatcher::delete_exact_match(const char *exact_str)
{
    return prefix_tree.del(exact_str, true);
}

bool
StringMatcher::delete_prefix_match(const char *prefix_str)
{
    return prefix_tree.del(prefix_str, false);
}
