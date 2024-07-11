class SampleClass {
    name: string;

    getNameOption() : string {
        return "Lancey"
    }

    getName() : string {
        return this.name
    }
}

function main() {
    let sampleObj = new SampleClass();
    sampleObj.name = sampleObj.getNameOption();
    console.log(sampleObj.getName());
    console.log(sampleObj.name)
}

main()
