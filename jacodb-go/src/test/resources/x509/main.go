package main

import (
	"crypto/x509"
	"fmt"
)

func main() {
	certificate := x509.Certificate{}
	_, err := certificate.Verify(x509.VerifyOptions{})
	if err != nil {
		fmt.Println("Error:", err)
	}
}
