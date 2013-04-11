#include "actions.h"
#include <string>
#include <iostream>
#include <sstream>

class MathOperation : public Operation
{
public:
    /**
     * constructs a new operation for current state current, that adds
     * x to the state
     */
    MathOperation(int *current, int x);

    /**
     * returns a string representation of this operation
     */
    std::string str() const;

    /**
     * undo this operation by subtracting the number originally added
     */
    void undo();

    /**
     * redo this operation by adding back the number
     */
    void redo();

private:
    int *current_;
    int x_;
    std::string rep_;
};

MathOperation::MathOperation(int *current, int x) :
    current_(current), x_(x)
{
    std::stringstream ss;
    ss << (x_ < 0 ? "- " : "+ ") << x_;
    rep_ = ss.str();
}

std::string
MathOperation::str() const
{
    return rep_;
}

void
MathOperation::undo()
{
    *current_ -= x_;
}

void
MathOperation::redo()
{
    *current_ += x_;
}

int
main(int argc, char *argv[])
{
    Actions<MathOperation> actions;
    std::string input;
    int current = 0;
    while (std::cin >> input) {
        if (input == "u") {
            if (actions.can_undo()) {
                actions.undo();
                if (actions.can_undo()) {
                    std::cout << "Next operation to undo is "
                              << actions.undo_peek() << std::endl;
                }
            } else {
                std::cout << "No more undo" << std::endl;
            }
        } else if (input == "r") {
            if (actions.can_redo()) {
                actions.redo();
                if (actions.can_redo()) {
                    std::cout << "Next operation to redo is "
                              << actions.redo_peek() << std::endl;
                }
            } else {
                std::cout << "No more redo" << std::endl;
            }
        } else {
            std::stringstream ss(input);
            int x;
            ss >> x;

            MathOperation op(&current, x);
            op.redo();
            actions.add(op);
        }

        std::cout << "Current sum: " << current << std::endl;
    }

    return 0;
}
