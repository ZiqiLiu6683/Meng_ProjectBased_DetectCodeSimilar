// Sample B
// Change variable names and function names, but keep the logic the same
#include <stdio.h>

int totalArray(int values[], int size) {
    int total = 0;
    for (int idx = 0; idx < size; idx++) {
        total += values[idx];
    }
    return total;
}

int main() {
    int nums[5] = {1, 2, 3, 4, 5};
    int output = totalArray(nums, 5);
    printf("%d\n", output);
    return 0;
}
