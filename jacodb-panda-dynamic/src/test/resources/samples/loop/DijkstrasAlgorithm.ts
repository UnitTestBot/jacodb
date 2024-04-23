function dijkstra(graph: number[][], startVertex: number): number[] {
    const distances: number[] = new Array(graph.length).fill(Infinity);
    distances[startVertex] = 0;
    const visited: boolean[] = new Array(graph.length).fill(false);

    for (let i = 0; i < graph.length - 1; i++) {
        let minDistance = Infinity, minIndex = -1;
        for (let v = 0; v < graph.length; v++) {
            if (!visited[v] && distances[v] <= minDistance) {
                minDistance = distances[v];
                minIndex = v;
            }
        }

        visited[minIndex] = true;
        for (let v = 0; v < graph.length; v++) {
            if (!visited[v] && graph[minIndex][v] !== 0 && distances[minIndex] + graph[minIndex][v] < distances[v]) {
                distances[v] = distances[minIndex] + graph[minIndex][v];
            }
        }
    }
    return distances;
}

const graph = [
    [0, 2, 4, 0],
    [2, 0, 2, 4],
    [4, 2, 0, 2],
    [0, 4, 2, 0]
];

console.log('Shortest paths from vertex 0:', dijkstra(graph, 0));
