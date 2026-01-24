#include <stdio.h>
#include <stdlib.h>

// input
int read_int() {
    int r;
    scanf("%d", &r);
    return r;
}

int *read_int_array(int n) {
    int *r = malloc(n * sizeof(int));
    int i;
    for (i=0; i<n; i++) {
        scanf("%d", &r[i]);
    }
    return r;
}

// output
int print_int(int i) {
    printf("%d", i);
    return 0;
}

int print_string(char* string) {
    printf("%s", string);
    return 0;
}

int print_newline() {
    printf("\r\n");
    return 0;
}

int print_true() {
    printf("ano");
    return 0;
}

int print_false() {
    printf("nie");
    return 0;
}

// allocations

// array of integers
int* allocate_int_array(int n) {
    return malloc(n * sizeof(int));
}

int* reallocate_int_array(int* ptr, int n) {
    return realloc(ptr, n * sizeof(int));
}

// array of chars / string / array of bools
char* allocate_char_array(int n) {
    return malloc(n * sizeof(char));
}

char* reallocate_char_array(char* ptr, int n) {
    return realloc(ptr, n * sizeof(char));
}

// array of arrays of whatever
void** allocate_pointer_array(int n) {
    return malloc(n * sizeof(void*));
}

void** reallocate_pointer_array(void** ptr, int n) {
    return realloc(ptr, n * sizeof(void*));
}

void see_you_later_allocator(void* p) {
    free(p);
}


