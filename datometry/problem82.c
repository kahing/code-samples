#include <assert.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct node
{
  int data;
  struct node *next;
} node;

static node *label(node *list)
{
  int i = 1;
  node *cur;

  for (cur = list; cur != NULL; cur = cur->next) {
    cur->data = i++;
  }

  return list;
}

static node *reverse(node *list)
{
  node *cur = list, *prev = NULL;

  while (cur != NULL) {
    node *next = cur->next;
    cur->next = prev;
    prev = cur;
    cur = next;
  }

  return prev;
}

// like reverse, but stops after n elements
// 1 -> 2 -> 3 -> 4
// n = 1:
//   1 -> NULL, 2
// n = 2:
//   2 -> 1 -> NULL, 3
typedef struct pair {
  node *first;
  node *second;
} pair;

static pair reverse_sublist(int n, node *list)
{
  node *cur = list, *prev = NULL;

  for (; n > 0 && cur != NULL; n--) {
    node *next = cur->next;
    cur->next = prev;
    prev = cur;
    cur = next;
  }

  pair ret = { prev, cur };
  return ret;
}

static node *reverseN(int N, node *list)
{
  node *cur = list, *prev = NULL;
  node *head = NULL;

  if (N < 2) {
    return list;
  }

  while (1) {
    pair ret = reverse_sublist(N, cur);

    if (prev != NULL) {
      prev->next = ret.first;
    } else {
      head = ret.first;
    }

    if (ret.second == NULL) {
      break;
    }

    prev = cur;
    cur = ret.second;
  }

  return head;
}

// count >= 1
static node *alloc_list(int count)
{
  node *head = calloc(1, sizeof(*head));
  node *cur = head;
  int i;

  for (i = 1; i < count; i++) {
    cur->next = calloc(1, sizeof(*head));
    cur = cur->next;
  }

  return head;
}

static node *default_list(int count)
{
  node *list = alloc_list(count);
  label(list);
  return list;
}

static node *make_list(int n, ...)
{
  va_list ap;
  va_start(ap, n);

  node *head = calloc(1, sizeof(*head));
  node *cur = head;

  while (1) {
      cur->data = n;
      n = va_arg(ap, int);
      if (n == 0) {
        break;
      }
      cur->next = calloc(1, sizeof(*head));
      cur = cur->next;
  }

  va_end(ap);

  return head;

}

static void print_list(node *list)
{
  node *cur;

  for (cur = list; cur != NULL; cur = cur->next) {
    printf("%d ", cur->data);
  }

  printf("\n");
}

static void assert_equal(node *list1, node *list2)
{
  node *cur1 = list1, *cur2 = list2;
  for (; cur1 && cur2; cur1 = cur1->next, cur2 = cur2->next) {
    assert(cur1->data == cur2->data);
  }

  assert(cur1 == NULL);
  assert(cur1 == cur2);
}

static void test_reverse_sublist()
{
  node *t2 = default_list(4);
  pair ret = reverse_sublist(1, t2);
  assert(ret.first->data == 1);
  assert(ret.first->next == NULL);
  assert_equal(ret.first, make_list(1, 0));
  assert(ret.second->data == 2);
  assert_equal(ret.second, make_list(2, 3, 4, 0));

  t2 = default_list(4);
  ret = reverse_sublist(2, t2);
  assert(ret.first->data == 2);
  assert(ret.first->next->data == 1);
  assert(ret.first->next->next == NULL);
  assert_equal(ret.first, make_list(2, 1, 0));
  assert(ret.second->data == 3);
  assert_equal(ret.second, make_list(3, 4, 0));

  t2 = default_list(2);
  ret = reverse_sublist(2, t2);
  assert(ret.first->data == 2);
  assert(ret.first->next->data == 1);
  assert(ret.first->next->next == NULL);
  assert(ret.second == NULL);
}

static void test_reverse_size(int n)
{
  assert_equal(default_list(n), reverse(reverse(default_list(n))));
}

static void test_reverse()
{
  test_reverse_size(6);
  test_reverse_size(1);

  assert_equal(reverse(make_list(1, 2, 3, 4, 5, 6, 0)), make_list(6, 5, 4, 3, 2, 1, 0));
  assert_equal(reverse(make_list(1, 0)), make_list(1, 0));
  assert(reverse(NULL) == NULL);
}

static void test_reverseN()
{
  node *t1 = default_list(6);
  t1 = reverseN(2, t1);
  assert_equal(t1, make_list(2, 1, 4, 3, 6, 5, 0));

  t1 = default_list(6);
  t1 = reverseN(3, t1);
  assert_equal(t1, make_list(3, 2, 1, 6, 5, 4, 0));

  t1 = default_list(7);
  t1 = reverseN(3, t1);
  assert_equal(t1, make_list(3, 2, 1, 6, 5, 4, 7, 0));

  t1 = make_list(1, 0);
  t1 = reverseN(1, t1);
  assert_equal(t1, make_list(1, 0));

  assert_equal(reverseN(11, default_list(5)), reverse(default_list(5)));
  assert_equal(reverseN(1, default_list(5)), default_list(5));
  assert_equal(reverseN(0, default_list(5)), default_list(5));

  assert(reverseN(2, NULL) == NULL);
}

int
main(int argc, char *argv[])
{
  // sanity checks
  node *t1 = alloc_list(6);
  print_list(t1);
  label(t1);
  print_list(t1);

  t1 = reverse(t1);
  print_list(t1);

  t1 = default_list(6);
  t1 = reverseN(2, t1);
  print_list(t1);

  print_list(make_list(1, 2, 0));

  test_reverse();
  test_reverse_sublist();
  test_reverseN();

  return 0;
}
