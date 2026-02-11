// Sample C
// Add new fundtion and all others are the same
#include <stdio.h>

int sumArray(int arr[], int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += arr[i];
    }
    return sum;
}

int multiplyArray(int arr[], int n) {
    int product = 1;
    for (int i = 0; i < n; i++) {
        product *= arr[i];
    }
    return product;
}

int main() {
    int data[5] = {1, 2, 3, 4, 5};
    int result = sumArray(data, 5);
    printf("%d\n", result);
    return 0;
}