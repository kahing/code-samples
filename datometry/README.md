Coding Problem #82
==================

Instructions
------------

Please develop a solution to the problems described below. You have 60 minutes
to return your code to the email address you received this description from.
In case you cannot finish, just send us what you got at that point; feel free
to send us improvements and fix-ups later on.

The problems are to be solved using ANSI C (no C++, no 3rd party libraries,
etc.). Please read the instructions carefully.


Assignment
----------

The following data structure defines elements of a linked list, using typical
text book convention:

  typedef struct node
    {
        int data;
            struct node *next;
              }
                node;



#1 Label
--------

Given a linked list, write a function with signature

  node *label(node *list);

that assigns consecutive integer values to all elements in the list starting
with 1.



#2 Reverse
----------

Given a linked list, write a function with signature

  node *reverse(node *list);

that reverses the given list.

Example:

 1 -> 2 -> 3 -> 4 -> 5 -> 6

is turned into

 6 -> 5 -> 4 -> 3 -> 2 -> 1



#3 Reverse in segments
----------------------

Given a linked list, write a function with signature

  node *reverseN(int N, node *list);

that reverses segments of size N.

Example:
For N = 2

 1 -> 2 -> 3 -> 4 -> 5 -> 6

is turned into

 2 -> 1 -> 4 -> 3 -> 6 -> 5


The code should be able to handle lists of arbitrary length.
