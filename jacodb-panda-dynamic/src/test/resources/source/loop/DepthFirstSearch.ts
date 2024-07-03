interface TreeNode {
    value: number;
    left?: TreeNode;
    right?: TreeNode;
}

function iterativeDFS(root: TreeNode | undefined): number[] {
    if (!root) return [];
    const stack: TreeNode[] = [root];
    const result: number[] = [];

    while (stack.length) {
        const node = stack.pop();
        if (node) {
            result.push(node.value);
            if (node.right) stack.push(node.right);  // Сначала добавляем правый узел, так как стек LIFO
            if (node.left) stack.push(node.left);
        }
    }
    return result;
}

const tree: TreeNode = {
    value: 1,
    left: {
        value: 2,
        left: { value: 4 },
        right: { value: 5 }
    },
    right: {
        value: 3,
        left: { value: 6 },
        right: { value: 7 }
    }
};

const traversalResult = iterativeDFS(tree);
console.log('Iterative DFS result:', traversalResult);
