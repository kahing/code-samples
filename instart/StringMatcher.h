#ifndef STRINGMATCHER_H
#define STRINGMATCHER_H

class StringMatcher
{
public:
    virtual void add_exact_match(const char *exact_str, int id);

    virtual void add_prefix_match(const char *prefix_str, int id);

    virtual int lookup(const char *input) const;

    virtual bool delete_exact_match(const char *exact_str);

    virtual bool delete_prefix_match(const char *prefix_str);

    bool compact() { return prefix_tree.compact(); }

private:
    struct prefix_node
    {
        prefix_node();
        prefix_node *add(const char *prefix_str);
        bool del(const char *prefix_str, bool exact);
        int lookup(const char *prefix_str) const;

        int prefix_val;
        int exact_val;
        // 'A' goes to index 0, 'a' goes to index 26
        struct prefix_node **children;

        int char_to_index(char c) const;
        /**
         * return true if node is empty
         */
        bool compact();
    };

    prefix_node prefix_tree;
};

#endif // STRINGMATCHER_H
