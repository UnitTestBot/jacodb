class User {
    private username: string;
    protected email: string;
    public name: string;

    constructor(username: string, email: string, name: string) {
        this.username = username;
        this.email = email;
        this.name = name;
    }
}

const user = new User("johndoe", "johndoe@example.com", "John Doe");
console.log(user.name);
// console.log(user.username);
// console.log(user.email);