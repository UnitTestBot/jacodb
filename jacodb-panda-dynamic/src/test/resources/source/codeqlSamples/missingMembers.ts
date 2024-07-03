function callingMembersOnPrimitiveTypes() {
    let number = 9
    console.log(number.getAura())
    console.log(number.aura)
    let aura = "aura"
    console.log(aura.getPoints())
    console.log(aura.points)
    let gyatt = true
    console.log(gyatt)
    console.log(gyatt.rizz)
    console.log(gyatt.setRizz(10))
}

function callingMembersOnNonObjectsTypes() {
    let skibidi = undefined
    console.log(skibidi.getNumber())
    console.log(skibidi.number)
    let arr = [1, 2, 3]
    console.log(arr.first() + arr.second)
}

function callingMembersOnNullType() {
    let npc = null
    let doubleValue = npc.getValue() + npc.value
}

function callingMembersOnObjects() {
    let user = {
        name: "Ken",
        age: 23,
        setStatus: function(status) {
            this.status = status;
            return `Status set to ${status}`;
        }
    };
    console.log(user.name);
    console.log(user.age);
    console.log(user.setStatus("x"));
    console.log(user.status);
}