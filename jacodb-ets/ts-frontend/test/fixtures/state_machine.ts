type StateName = "idle" | "running" | "paused" | "stopped";

interface Transition {
    from: StateName;
    to: StateName;
    guard?: (payload: unknown) => boolean;
}

export class StateMachine {
    current: StateName = "idle";
    private transitions: Transition[] = [];
    private listeners: ((from: StateName, to: StateName) => void)[] = [];

    allow(from: StateName, to: StateName): void {
        this.transitions.push({ from, to });
    }

    onChange(listener: (from: StateName, to: StateName) => void): void {
        this.listeners.push(listener);
    }

    fire(to: StateName, payload?: unknown): boolean {
        let matched: Transition | undefined;
        for (const t of this.transitions) {
            if (t.from === this.current && t.to === to) {
                matched = t;
                break;
            }
        }
        if (matched === undefined) {
            return false;
        }
        try {
            if (matched.guard !== undefined && !matched.guard(payload)) {
                return false;
            }
        } catch (e) {
            console.error(`guard failed: ${e}`);
            return false;
        } finally {
            console.log(`transition attempt: ${this.current} -> ${to}`);
        }
        const from = this.current;
        this.current = to;
        for (const listener of this.listeners) {
            listener(from, to);
        }
        return true;
    }
}

export function demo(): StateName {
    const machine = new StateMachine();
    machine.allow("idle", "running");
    machine.allow("running", "paused");
    machine.onChange((from, to) => {
        console.log(`changed: ${from} -> ${to}`);
    });
    machine.fire("running");
    const label = machine.current === "running" ? "started" : "not started";
    console.log(label);
    return machine.current;
}
