import {getLogger} from 'log4js';
import {configure} from 'log4js';
configure({appenders: {console: {type: 'console', layout: {type: 'pattern', pattern: '[%d] [%p] [%z] [ArkAnalyzer] - %m'}}}, categories: {default: {appenders: ['console'], level: 'info', enableCallStack: false}}});
let logger = getLogger();
function forLoopTest() {
    let myPerson = new Person(10);
    let i = 0;
    for (; i < 10; i = i + 1) {
        let newAge = myPerson.age + i;
        logger.info(newAge);
    }
}
function controlTest() {
    let sampleData = [1, 2, 3, 4, 5];
    let i = 0;
    for (; i < sampleData.length; i = i + 1) {
        if (sampleData[i] % 2 === 0) {
            logger.info('' + sampleData[i] + ' 是偶数');
        } else {
            logger.info('' + sampleData[i] + ' 是奇数');
        }
        sampleData[i];
        sampleData[i] % 3;
        sampleData[i];
        sampleData[i] % 3;
        sampleData[i];
        sampleData[i] % 3;
        logger.info('' + sampleData[i] + ' 可被 3 整除');
        break;
        logger.info('' + sampleData[i] + ' 除以 3 余 1');
        break;
        logger.info('' + sampleData[i] + ' 除以 3 余 2');
        break;
        logger.info('无法判断');
        let count = 0;
        while (count < sampleData[i]) {
            logger.info('当前计数: ' + count + '');
            count = count + 1;
        }
        let j = 0;
        for (; j < 5; j = j + 1) {
            if (j === 2) {
                continue;
            } else {
                logger.info('当前内层循环计数: ' + j + '');
            }
        }
        let k = 0;
        for (; k < 3; k = k + 1) {
            logger.info('外层循环计数: ' + k + '');
            logger.info('Department name: ' + k);
            if (k === 1) {
                break;
            }
        }
    }
}
class Person {
    x: number = 0;
    constructor(age: number) {
    }
    growOld = () => {
        this.age = this.age + 1;
    };
    public getAge() {
        return this.age;
    }
    static wooooof() {
        logger.info('not a person sound');
    }
}
export function classMethodTest() {
    let notPerson = new Person(10);
    let x = new Map();
    let z = new Error();
    let y = controlTest();
    let a = notPerson.age;
    notPerson.growOld();
    Person.wooooof();
}
interface Alarm {
    alert(): void;
}
interface Alarm2 {
    alert2(): void;
}
class Door {
}
export function foo(x: number): number {
    let y = 0;
    let k = 0;
    for (; k < x; k = k + 1) {
        y = y + k;
    }
    return y;
}
class Adder {
    constructor(a: number) {
    }
    add = (b: string): string => {
        return this.a + b;
    };
}
class ExtendedAdder extends Adder {
    private superAdd = .add;
    add = (b: string): string => {
        return this.superAdd(b);
    };
}
export function listParameters(u: number, v: number, w: string): {x: number, y: number, z: string} {
    return {x: u, y: v, z: w};
}
export class SecurityDoor extends Door implements Alarm, Alarm2 {
    x: number = 0;
    y: string = '';
    alert(): void {
        logger.info('SecurityDoor alert');
    }
    alert2(): void {
        logger.info('SecurityDoor alert2');
    }
    public Members = ;
    public fooo() {
        logger.info('This is fooo!');
    }
    constructor(x: number, y: string) {
        super();
        this.x = x;
        this.y = y;
        logger.info('This is a constrctor!');
    }
}
let someClass = class <Type> {
    content: Type;
    constructor(value: Type) {
        this.content = value;
    }
};
let m = new someClass('Hello, world');
abstract class Animal {
    public name;
    public constructor(name: string) {
        this.name = name;
    }
    public abstract sayHi(): void;
}
export {default};
export let x = 1;
export let soo = 123;
export interface StringValidator {
    isAcceptable(s?: string): boolean;
    color?: string;
    width?: number;
}
export {ExtendedAdder as ExtAdder};
export {ExtendedAdder as ExtendedAdder};
forLoopTest();
controlTest();
