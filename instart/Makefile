CXX=g++ -Wall -Werror -g -O2

test: StringMatcher.o test.o
	g++ StringMatcher.o test.o -o test

bench: StringMatcher.o bench.o
	g++ StringMatcher.o bench.o -o bench

clean:
	$(RM) test StringMatcher.o test.o bench.o bench

run: test
	./test

run-bench: bench
	./bench /usr/share/dict/american-english test 10000000
