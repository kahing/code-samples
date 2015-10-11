#include <set>
#include <cassert>
#include <cstdlib>
#include <algorithm>
#include <cmath>

using namespace std;

class Commit {
public:
  Commit *left_parent_;
  Commit *right_parent_;
  bool good_;
  int depth_;

  Commit(Commit *left, Commit *right) : left_parent_(left), right_parent_(right) {
    // a commit is bad iff either of the parent is bad
    good_ = !(left_parent_ != nullptr && !left_parent_->good_ ||
              right_parent_ != nullptr && !right_parent_->good_);
    if (left_parent_ && right_parent_) {
      depth_ = max(left_parent_->depth_, right_parent_->depth_) + 1;
    } else {
      depth_ = left_parent_ == nullptr ? 0 : left_parent_->depth_ + 1;
    }
  }

  Commit *bad() {
    good_ = false;
    return this;
  }

  bool isAncestorOf(Commit *c);

  Commit *newCommit()  {
    return new Commit(this, nullptr);
  }

  Commit *merge(Commit *another)  {
    return new Commit(this, another);
  }

  void insertAllAncestors(set< Commit *> &allCommits)  {
    allCommits.insert(this);
    if (left_parent_) {
      left_parent_->insertAllAncestors(allCommits);
    }
    if (right_parent_) {
      right_parent_->insertAllAncestors(allCommits);
    }
  }

};

bool Commit::isAncestorOf(Commit *c)  {
  if (c == nullptr) {
    return false;
  }
  if (this == c) {
    return true;
  }

  return isAncestorOf(c->left_parent_) || isAncestorOf(c->right_parent_);
}

Commit *firstKnownGood(set<Commit *> goodCommits, Commit *bad) {
  if (goodCommits.find(bad) != goodCommits.end()) {
    return bad;
  }

  Commit *left = firstKnownGood(goodCommits, bad->left_parent_);
  if (!bad->right_parent_) {
    return left;
  }
  Commit *right = firstKnownGood(goodCommits, bad->right_parent_);
  if (left->depth_ >= right->depth_) {
    return left;
  } else {
    return right;
  }
}

// consider all branches evenly and count backwards of delta, but never get to c,
// if we get to c first, return amount of leftover
pair<Commit*, int> findNext(Commit *c, Commit *start, int delta) {
  if (start == c || start == nullptr) {
    return make_pair(nullptr, delta);
  }
  if (delta == 0) {
    return make_pair(start, 0);
  }

  if (start->right_parent_ == nullptr) {
    return findNext(c, start->left_parent_, delta - 1);
  } else {
    auto left = findNext(c, start->left_parent_, delta / 2);
    auto right = findNext(c, start->right_parent_, delta / 2);
    if (left.first == nullptr) {
      // pick up the remainders
      return findNext(c, right.first, left.second);
    } else if (right.first == nullptr) {
      return findNext(c, left.first, right.second);
    } else {
      if (left.first->depth_ > right.first->depth_) {
        return left;
      } else {
        return right;
      }
    }
  }

}

// Bisect returns a commit c such that the following holds:
// c is in allCommits
// For all commits g in goodCommits c is not an ancestor of g
// c is an ancestor of badCommit
// Of the commits that are ancestors of badCommit and not ancestors of any good commits.
// About half are ancestors of c and half aren't.
Commit *Bisect(set< Commit *> allCommits, set<Commit *> goodCommits,  Commit *badCommit) {
  Commit *c = firstKnownGood(goodCommits, badCommit);
  assert(c->depth_ != badCommit->depth_);
  int delta = (badCommit->depth_ - c->depth_);
  if (delta == 1) {
    if (badCommit->left_parent_ == c) {
      if (badCommit->right_parent_ != nullptr) {
        return badCommit->right_parent_;
      } else {
        return badCommit;
      }
    } else {
      return badCommit->left_parent_;
    }
  }
  if (delta == 0) {
    return badCommit;
  }

  auto mid = findNext(c, badCommit, delta / 2);
  assert(mid.first != nullptr);
  return mid.first;
}

bool bisectCondition(set< Commit *> allCommits, set<Commit *> goodCommits,  Commit *c) {
  if (allCommits.find(c) == allCommits.end()) {
    return false;
  }

  for (auto iter = goodCommits.begin(); iter != goodCommits.end(); ++iter) {
    if (c->isAncestorOf(*iter)) {
      return false;
    }
  }

  return true;
}

Commit * BisectStupid(set< Commit *> allCommits, set<Commit *> goodCommits,  Commit *badCommit) {
  assert(allCommits.find(badCommit) != allCommits.end());

  if (bisectCondition(allCommits, goodCommits, badCommit->left_parent_)) {
    return badCommit->left_parent_;
  }

  if (bisectCondition(allCommits, goodCommits, badCommit->right_parent_)) {
    return badCommit->right_parent_;
  }

  return badCommit;
}

typedef  Commit *(*Bisector)( set< Commit *> allCommits, set<Commit *> goodCommits,
                                   Commit *badCommit);

// finds the first bad commit
Commit *findBad(set< Commit *> &allCommits, set<Commit *> &goodCommits,  Commit *badCommit,
                Bisector bisector) {
  int depth = badCommit->depth_;

  for (int i = 0; ; i++) {
    Commit *mid = bisector(allCommits, goodCommits, badCommit);
    if (mid->good_) {
      goodCommits.insert(mid);
    } else {
      if (mid == badCommit) {
        assert(i <= ((log(depth) / log(2)) + 1));
        return mid;
      } else {
        badCommit = mid;
      }
    }
  }
}

Commit *findBad( Commit *root,  Commit *bad, Bisector bisector) {
  if (!root->good_) {
    return root;
  }

  set< Commit *> allCommits, goodCommits;
  bad->insertAllAncestors(allCommits);
  if (root->good_) {
    goodCommits.insert(root);
    assert(firstKnownGood(goodCommits, bad) == root);
  }

  return findBad(allCommits, goodCommits, bad, bisector);
}

int
main(int argc, char *argv[]) {
  // build a simple tree

  Commit *root = new Commit(nullptr, nullptr);
  Commit *left = root->newCommit(), *right = root->newCommit();
  right->good_ = false;
  Commit *final = left->merge(right);

  assert(findBad(root, final, BisectStupid) == right);
  assert(findNext(root, final, 1).first == right);
  assert(findBad(root, final, Bisect) == right);

  root = new Commit(nullptr, nullptr);
  final = root->bad()->newCommit()->newCommit();
  assert(findBad(root, final, Bisect) == root);

  root = new Commit(nullptr, nullptr);
  Commit *first = root->newCommit()->bad();
  final = first->newCommit()->newCommit()->newCommit();
  assert(findBad(root, final, Bisect) == first);

  root = new Commit(nullptr, nullptr);
  right = root->newCommit()->bad();
  left = root;
  for (int i = 0; i < 10; i++) {
    left = left->newCommit();
  }
  final = left->merge(right);
  auto res = findBad(root, final, Bisect);
  assert(res == right);

  root = new Commit(nullptr, nullptr);
  left = root->newCommit()->bad();
  right = root;
  for (int i = 0; i < 10; i++) {
    right = right->newCommit();
  }
  final = left->merge(right);
  res = findBad(root, final, Bisect);
  assert(res == left);

  root = new Commit(nullptr, nullptr);
  left = root->newCommit()->bad();
  right = root;
  for (int i = 0; i < 1000; i++) {
    right = right->newCommit();
  }
  final = left->merge(right);
  res = findBad(root, final, Bisect);
  assert(res == left);

  root = new Commit(nullptr, nullptr);
  left = root;
  for (int i = 0; i < 10; i++) {
    left = left->newCommit();
  }
  assert(left->depth_ == 10);
  right = left->newCommit()->newCommit()->bad();
  assert(right->depth_ == 12);
  right = right->newCommit()->merge(right->newCommit());
  assert(right->depth_ == 14);
  left = left->newCommit()->newCommit()->newCommit();
  assert(left->depth_ == 13);
  final = left->merge(right);
  assert(final->depth_ == 15);
  res = findBad(root, final, Bisect);
  assert(res->depth_ == 12);
}
