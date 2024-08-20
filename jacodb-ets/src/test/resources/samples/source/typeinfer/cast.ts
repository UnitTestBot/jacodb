declare function getData(): any;

type Data = {}

function entrypoint() {
    let x = getData() as Data;
    infer(x);
}

function infer(arg: any) {
    console.log(arg);
}
