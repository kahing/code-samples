#include <set>
#include <cassert>

using namespace std;

class Commit {
public:
  Commit *left_parent_;
  Commit *right_parent_;
  bool good_;

  Commit(Commit *left, Commit *right) : left_parent_(left), right_parent_(right) {
    // a commit is bad iff either of the parent is bad
    good_ = !(left_parent_ != nullptr && !left_parent_->good_ ||
              right_parent_ != nullptr && !right_parent_->good_);
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


Commit * Bisect( set< Commit *> allCommits, set<Commit *> goodCommits,  Commit *badCommit) {
  return *goodCommits.begin();
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
  while (true) {
    Commit *mid = bisector(allCommits, goodCommits, badCommit);
    if (mid->good_) {
      goodCommits.insert(mid);
    } else {
      if (mid == badCommit) {
        return mid;
      } else {
        badCommit = mid;
      }
    }
  }
}

Commit *findBad( Commit *root,  Commit *bad, Bisector bisector) {
  set< Commit *> allCommits, goodCommits;
  bad->insertAllAncestors(allCommits);
  if (root->good_) {
    goodCommits.insert(root);
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
}
