interface Graph {
    [key: string]: string[];
}

function bfs(graph: Graph, startNode: string): string[] {
    const visited = [];
    const queue = [startNode];

    while (queue.length > 0) {
        const node = queue.shift();
        if (!visited.includes(node)) {
            visited.push(node);
            const neighbours = graph[node];
            for (const neighbour of neighbours) {
                if (!visited.includes(neighbour)) {
                    queue.push(neighbour);
                }
            }
        }
    }

    return visited;
}

const graph: Graph = {
    a: ['b', 'c'],
    b: ['d'],
    c: ['e'],
    d: ['f'],
    e: [],
    f: []
};

const visitOrder = bfs(graph, 'a');
console.log('Visit order:', visitOrder);
