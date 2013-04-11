#ifndef ACTIONS_H
#define ACTIONS_H

#include <list>
#include <string>
#include <iostream>

class Operation {
public:
    /* returns a string representation of this operation */
    virtual std::string str() const = 0;

    /* undo this operation */
    virtual void undo() = 0;

    /* redo/apply this operation */
    virtual void redo() = 0;
};

template <typename O>
class Actions {
public:
    Actions();

    /**
     * add an operation at the current undo/redo location. all redo
     * operations from this point on are deleted. 
     */
    void add(const O &op);

    /**
     * returns if there are operations to undo
     */
    bool can_undo() const;

    /**
     * undo the last operation
     */
    void undo();

    /**
     * peeks at what the next undo operation looks like
     */
    std::string undo_peek() const;

    /**
     * returns if there are operations to redo
     */
    bool can_redo() const;

    /**
     * redo the last undone operation
     */
    void redo();

    /**
     * peeks at what the next redo operation looks like
     */
    std::string redo_peek() const;

private:
    void debug_state() const;

    std::list<O> operations_;
    /** cur_ should point to the next operation to be undone */
    typename std::list<O>::iterator cur_;
};

template <typename O>
Actions<O>::Actions() : cur_(operations_.begin())
{
}

template <typename O>
void
Actions<O>::add(const O &op)
{
    operations_.erase(cur_, operations_.end());
    operations_.push_back(op);
    cur_ = operations_.end();

    debug_state();
}

template <typename O>
void
Actions<O>::undo()
{
    if (!can_undo()) {
        throw "No more operations to undo";
    }

    --cur_;
    cur_->undo();

    debug_state();
}

template <typename O>
bool
Actions<O>::can_undo() const
{
    return cur_ != operations_.begin();
}

template <typename O>
std::string
Actions<O>::undo_peek() const
{
    if (!can_undo()) {
        throw "No more operations to undo";
    }

    typename std::list<O>::iterator tmp = cur_;
    --tmp;
    return tmp->str();
}

template <typename O>
bool
Actions<O>::can_redo() const
{
    return cur_ != operations_.end();
}

template <typename O>
std::string
Actions<O>::redo_peek() const
{
    if (!can_redo()) {
        throw "No more operations to undo";
    }
    return cur_->str();
}

template <typename O>
void
Actions<O>::redo()
{
    if (!can_redo()) {
        throw "No more operations to redo";
    }

    cur_->redo();
    ++cur_;

    debug_state();
}

template <typename O>
void
Actions<O>::debug_state() const
{
#if DEBUG
    for (typename std::list<O>::const_iterator i = operations_.begin();
         i != operations_.end(); ++i) {
        std::cout << (i == cur_ ? "^" : "") << i->str() << ", ";
    }
    std::cout << std::endl;
#endif
}

#endif
