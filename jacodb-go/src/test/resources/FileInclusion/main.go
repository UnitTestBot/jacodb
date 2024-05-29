package main

import (
	"io/ioutil"
	"log"
	"os"
)

func main() {
	f := os.Getenv("tainted_file")
	body, err := ioutil.ReadFile(f)
	if err != nil {
		log.Printf("Error: %v\n", err)
	}
	log.Print(body)

}
