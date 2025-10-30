interface Graph {
    [key: string]: string[];
}

function bfs(graph: Graph, startNode: string): string[] {
    const visited = new Set<string>();
    const queue = [startNode];
    const order = [];

    while (queue.length > 0) {
        const node = queue.shift();
        if (!visited.has(node)) {
            visited.add(node);
            order.push(node);
            const neighbours = graph[node];
            for (const neighbour of neighbours) {
                if (!visited.has(neighbour)) {
                    queue.push(neighbour);
                }
            }
        }
    }

    return order;
}

const graph: Graph = {
    a: ['b', 'c'],
    b: ['d'],
    c: ['e'],
    d: ['f'],
    e: [],
    f: [],
};

const visitOrder = bfs(graph, 'a');
console.log('Visit order:', visitOrder); // [ 'a', 'b', 'c', 'd', 'e', 'f' ]
