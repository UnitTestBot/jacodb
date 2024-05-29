package main

import "fmt"

func getLogin() string {
	return "admin"
}

func GetPassword(login string) string {
	if login == "admin" {
		return "f62e5bcda4fae4f82370da0c6f20697b8f8447ed"
	}
	return "f62e5bcda4fae4f82370da0c6f20697b8f8447ef"
}

func printPassword(s string) {
	fmt.Println(s)
}

func main() {
	login := getLogin()
	password := GetPassword(login)
	printPassword(password)
}
