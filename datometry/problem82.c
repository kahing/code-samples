#include <assert.h>
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

static node *last(node *list)
{
  for (; list->next != NULL; list = list->next) {}
  return list;
}

static node *reverseN(int N, node *list)
{
  node *cur = list, *prev = NULL;
  node *head = NULL;

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

    prev = last(ret.first);
    cur = ret.second;
  }

  return head;
}

// count >= 1
static node *make_list(int count)
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

static void print_list(node *list)
{
  node *cur;

  for (cur = list; cur != NULL; cur = cur->next) {
    printf("%d ", cur->data);
  }

  printf("\n");
}

static void test_reverse_sublist()
{
  node *t2 = make_list(4);
  label(t2);
  pair ret = reverse_sublist(1, t2);
  assert(ret.first->data == 1);
  assert(ret.first->next == NULL);
  assert(ret.second->data == 2);

  t2 = make_list(4);
  label(t2);
  ret = reverse_sublist(2, t2);
  assert(ret.first->data == 2);
  assert(ret.first->next->data == 1);
  assert(ret.first->next->next == NULL);
  assert(ret.second->data == 3);

  t2 = make_list(2);
  label(t2);
  ret = reverse_sublist(2, t2);
  assert(ret.first->data == 2);
  assert(ret.first->next->data == 1);
  assert(ret.first->next->next == NULL);
  assert(ret.second == NULL);
}

int
main(int argc, char *argv[])
{
  node *t1 = make_list(6);
  print_list(t1);
  label(t1);
  print_list(t1);

  t1 = reverse(t1);
  print_list(t1);

  test_reverse_sublist();

  t1 = make_list(6);
  label(t1);
  t1 = reverseN(2, t1);
  print_list(t1);

  return 0;
}
