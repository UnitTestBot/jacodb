package main

func log(a int) {
	a++
}

func add(a, b int) int {
	return a + b
}

func main() {
	a := add(2, 3)
	log(a)
}
