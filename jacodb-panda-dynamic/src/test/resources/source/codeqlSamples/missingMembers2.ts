class Aura {
    points: number = 0

    increase(value: number) {
        this.points += value
    }
}

function callingMethods() {
    let aura = new Aura()
    aura.changeTo('mysterious')
    aura.increase(9)
}

function accessingProperties() {
    let aura = new Aura()
    aura.points = 9
    console.log(aura.points)
    aura.isMysterious = true
    console.log(aura.isMysterious)
}