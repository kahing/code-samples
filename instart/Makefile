CXX=g++ -Wall -Werror -g -O2 --std=c++11 -fno-omit-frame-pointer

test: StringMatcher.o test.o
	g++ -g StringMatcher.o test.o -o test

test2: StringMatcher2.o test2.o
	g++ -g StringMatcher2.o test2.o -o test2

bench: StringMatcher.o bench.o
	g++ -g StringMatcher.o bench.o -o bench

bench2: StringMatcher2.o bench2.o
	g++ -g StringMatcher2.o bench2.o -o bench2

clean:
	$(RM) test StringMatcher.o test.o bench.o bench \
		test2 StringMatcher2.o test2.o bench2.o bench2

run: test
	./test

run-bench: bench
	./bench /usr/share/dict/american-english test 10000000

run-bench2: bench2
	./bench2 /usr/share/dict/american-english test 10000000
