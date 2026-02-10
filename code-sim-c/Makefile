CC=clang
CFLAGS=-std=c11 -O2 -Wall -Wextra -Iinclude
TARGET=build/sim

SRCS=src/main.c
OBJS=$(SRCS:src/%.c=build/%.o)

all: $(TARGET)

build:
	mkdir -p build

build/%.o: src/%.c | build
	$(CC) $(CFLAGS) -c $< -o $@

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) $^ -o $@

run: all
	./$(TARGET)

clean:
	rm -rf build
