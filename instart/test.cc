#include "StringMatcher.h"
#include <stdio.h>
#include <assert.h>

int
main(int argc, char **argv)
{
    {
        StringMatcher matcher;
        matcher.add_exact_match("contactus", 3);
        matcher.add_exact_match("contactus", 1);
        matcher.add_prefix_match("contactus", 5);
        matcher.add_prefix_match("contactus", 2);
        assert(matcher.lookup("contactus") == 1);
        assert(matcher.lookup("contactusplease") == 2);
        assert(matcher.delete_prefix_match("contactus"));
        assert(!matcher.compact());
        assert(matcher.lookup("contactusplease") == -1);
        assert(matcher.delete_exact_match("contactus"));
        assert(!matcher.delete_exact_match("contactus"));
        assert(!matcher.delete_prefix_match("contactus"));
        assert(!matcher.delete_prefix_match("contactus"));
        assert(matcher.compact());
    }

    {
        StringMatcher matcher;
        matcher.add_prefix_match("img", 1);
        assert(matcher.lookup("imgcutepuppy") == 1);
        assert(matcher.lookup("htmlcutepuppy") == -1);
    }

    {
        StringMatcher matcher;
        matcher.add_prefix_match("img", 1);
        matcher.add_prefix_match("imghd", 2);
        assert(matcher.lookup("imgcutepuppy") == 1);
        assert(matcher.lookup("imghdcutepuppy") == 2);
        assert(matcher.delete_prefix_match("img"));
        assert(matcher.lookup("imghdcutepuppy") == 2);
        assert(matcher.delete_prefix_match("imghd"));
        assert(!matcher.delete_prefix_match("imghd"));
    }

    return 0;
}
