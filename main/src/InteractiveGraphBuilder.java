import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class InteractiveGraphBuilder extends JPanel {
    private final List<Node> nodes;
    private final List<Edge> edges;
    private Node selectedNode;
    private boolean isSelectingEdge;
    private boolean isGraphConfirmed;

    private List<List<Node>> allTopologicalSorts = new ArrayList<>();
    private Thread sortingThread;  // 排序线程
    private volatile boolean isSorting = false;  // 控制排序的标志

    // 节点类
    private static class Node {
        int x, y;
        String label;
        Color color; // 节点的颜色
        boolean copy = false;

        Node(int x, int y, String label) {
            this.x = x;
            this.y = y;
            this.label = label;
            this.color = Color.BLACK;
        }

        Node(Node node) {
            this.x = node.x;
            this.y = node.y;
            this.label = node.label;
            this.color = node.color;
            copy = true;
        }

        void draw(Graphics g) {

            g.setColor(color);
            g.fillOval(x - 15, y - 15, 30, 30);
            g.setColor(Color.WHITE);
            g.drawString(label, x - 5, y + 5);
        }
    }

    // 边类（有向边）
    private static class Edge {
        Node from, to;

        Edge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Edge other = (Edge) obj;
            return from.equals(other.from) && to.equals(other.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        void draw(Graphics g) {
            g.setColor(Color.BLACK);
            g.drawLine(from.x, from.y, to.x, to.y);
            drawArrow(g, from, to);
        }

        private void drawArrow(Graphics g, Node from, Node to) {
            final int arrowLength = 10;
            final int nodeRadius = 15;
            final double angle = Math.atan2(to.y - from.y, to.x - from.x);
            int offsetX = (int) (nodeRadius * Math.cos(angle));
            int offsetY = (int) (nodeRadius * Math.sin(angle));
            int adjustedX = to.x - offsetX;
            int adjustedY = to.y - offsetY;

            int x1 = (int) (adjustedX - arrowLength * Math.cos(angle - Math.PI / 6));
            int y1 = (int) (adjustedY - arrowLength * Math.sin(angle - Math.PI / 6));
            int x2 = (int) (adjustedX - arrowLength * Math.cos(angle + Math.PI / 6));
            int y2 = (int) (adjustedY - arrowLength * Math.sin(angle + Math.PI / 6));

            g.setColor(Color.BLACK);
            g.fillPolygon(new int[] { adjustedX, x1, x2 }, new int[] { adjustedY, y1, y2 }, 3);
        }
    }

    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME = 200; // 最大双击时间（毫秒）

    public InteractiveGraphBuilder() {
        nodes = new ArrayList<>();
        edges = new ArrayList<>();
        selectedNode = null;
        isSelectingEdge = false;
        isGraphConfirmed = false;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isGraphConfirmed) return;
                if (isSelectingEdge) {
                    for (Node node : nodes) {
                        if (Math.abs(e.getX() - node.x) < 20 && Math.abs(e.getY() - node.y) < 20) {
                            if (selectedNode == node) {
                                isSelectingEdge = false;
                                selectedNode = null;
                                repaint();
                                return;
                            }

                            Edge newEdge = new Edge(selectedNode, node);
                            boolean edgeExists = false;

                            for (Edge edge : edges) {
                                if (edge.equals(newEdge)) {
                                    edgeExists = true;
                                    break;
                                }
                            }

                            if (edgeExists) {
                                edges.removeIf(edge -> edge.equals(newEdge));
                            } else {
                                edges.add(newEdge);
                            }

                            isSelectingEdge = false;
                            selectedNode = null;
                            repaint();
                            break;
                        }
                    }
                } else {
                    boolean clickedOnNode = false;
                    for (Node node : nodes) {
                        if (Math.abs(e.getX() - node.x) < 20 && Math.abs(e.getY() - node.y) < 20) {
                            clickedOnNode = true;
                            break;
                        }
                    }

                    if (!clickedOnNode) {
                        String label = JOptionPane.showInputDialog("请输入节点标签:");
                        if (label != null && !label.trim().isEmpty()) {
                            for (Node node : nodes) {
                                if (node.label.equals(label)) {
                                    JOptionPane.showMessageDialog(null,"无法使用重复标签");
                                    return;
                                }
                            }
                            Node newNode = new Node(e.getX(), e.getY(), label);
                            nodes.add(newNode);
                            repaint();
                        }
                    } else {
                        for (Node node : nodes) {
                            if (Math.abs(e.getX() - node.x) < 20 && Math.abs(e.getY() - node.y) < 20) {
                                selectedNode = node;
                                isSelectingEdge = true;
                                repaint();
                                break;
                            }
                        }
                    }
                }
            }



            @Override
            public void mouseClicked(MouseEvent e) {

                long currentTime = System.currentTimeMillis();

                // 判断两次点击之间的时间差
                if (currentTime - lastClickTime <= DOUBLE_CLICK_TIME) {
                    for (Node node : nodes) {
                        if (Math.abs(e.getX() - node.x) < 20 && Math.abs(e.getY() - node.y) < 20) {
                            int response = JOptionPane.showConfirmDialog(null,
                                    "确认删除节点 " + node.label + " 及其相关的边？",
                                    "删除节点",
                                    JOptionPane.YES_NO_OPTION);
                            if (response == JOptionPane.YES_OPTION) {
                                nodes.remove(node);
                                edges.removeIf(edge -> edge.from == node || edge.to == node);
                                repaint();
                            }
                            break;
                        }
                    }
                }

                lastClickTime = currentTime; // 更新点击时间
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Node hoveredNode = null;
                for (Node node : nodes) {
                    if (Math.abs(e.getX() - node.x) < 20 && Math.abs(e.getY() - node.y) < 20) {
                        hoveredNode = node;
                        break;
                    }
                }
                if (selectedNode != null) {
                    repaint();
                }
            }
        });
    }

    private boolean isAOVNetwork() {
        Map<Node, Integer> inDegree = new HashMap<>();
        for (Node node : nodes) {
            inDegree.put(node, 0);
        }
        for (Edge edge : edges) {
            inDegree.put(edge.to, inDegree.get(edge.to) + 1);
        }

        Queue<Node> queue = new LinkedList<>();
        for (Node node : nodes) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        int processedNodes = 0;
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            processedNodes++;
            for (Edge edge : edges) {
                if (edge.from == current) {
                    Node neighbor = edge.to;
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        queue.add(neighbor);
                    }
                }
            }
        }
        return processedNodes == nodes.size();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        // 启用抗锯齿效果
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Edge edge : edges) {
            edge.draw(g2d);
        }
        for (Node node : nodes) {
            node.draw(g2d);
        }
        if (selectedNode != null) {
            g2d.setColor(Color.RED);
            g2d.fillOval(selectedNode.x - 15, selectedNode.y - 15, 30, 30);
            g2d.setColor(Color.WHITE);
            g2d.drawString(selectedNode.label, selectedNode.x - 5, selectedNode.y + 5);
        }
        g2d.setColor(Color.DARK_GRAY);
        Font title = new Font("SansSerif",Font.BOLD, 22);
        g2d.setFont(title);
        g2d.drawString("当前拓扑排序：", 20, 40);
        Font result = new Font("宋体",Font.TRUETYPE_FONT, 20);
        g2d.setFont(result);
        g2d.setColor(Color.BLUE);
        int yOffset = 66;
        for (List<Node> sort : allTopologicalSorts) {
            StringBuilder sb = new StringBuilder();
            for (Node node : sort) {
                sb.append(node.label).append(" ");
            }
            g.drawString(sb.toString(), 22, yOffset);
            yOffset += 20;
        }
    }

    private boolean goingToStop = false;

    public void findAllTopologicalSorts() {
        if (!isAOVNetwork()) {
            JOptionPane.showMessageDialog(this, "图中存在环，无法进行拓扑排序！");
            goingToStop = false;
            return;
        }
        Map<Node, Integer> inDegree = new HashMap<>();
        for (Node node : nodes) {
            inDegree.put(node, 0);
        }
        for (Edge edge : edges) {
            inDegree.put(edge.to, inDegree.get(edge.to) + 1);
        }

        List<Node> currentSort = new ArrayList<>();
        Set<Node> visited = new HashSet<>();

        sortingThread = new Thread(() -> backtrack(inDegree, currentSort, visited));
        isSorting = true;
        sortingThread.start();
    }
    private void returnBlack(){
        for (Node node : nodes) {
            node.color = Color.BLACK;
        }
        repaint();
    }

    private void backtrack(Map<Node, Integer> inDegree, List<Node> currentSort, Set<Node> visited) {
        if (currentSort.size() == nodes.size()) {
            allTopologicalSorts.add(new ArrayList<>(currentSort));
            try {
                Thread.sleep(1000);
                if (goingToStop) {
                    returnBlack();
                    Thread.interrupted();
                    return;
                }
            } catch (InterruptedException e) {
                return;  // 排序被中断时，直接返回
            }
            repaint();
            return;
        }
        try {
            for (Node node : nodes) {
                if (!visited.contains(node) && inDegree.get(node) == 0) {
                    visited.add(node);
                    currentSort.add(node);
                    node.color = Color.RED;
                    repaint();
                    Thread.sleep(1000);
                    if (goingToStop) {
                        returnBlack();
                        Thread.interrupted();
                        return;
                    }


                    for (Edge edge : edges) {
                        if (edge.from == node) {
                            inDegree.put(edge.to, inDegree.get(edge.to) - 1);
                        }
                    }
                    backtrack(inDegree, currentSort, visited);
                    visited.remove(node);
                    currentSort.remove(currentSort.size() - 1);
                    node.color = Color.BLACK;
                    repaint();

                    for (Edge edge : edges) {
                        if (edge.from == node) {
                            inDegree.put(edge.to, inDegree.get(edge.to) + 1);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            System.out.println("排序被打断");
        }
    }

    public void clearGraph() {
        nodes.clear();
        edges.clear();
        allTopologicalSorts.clear();
        repaint();
        if (sortingThread != null && sortingThread.isAlive()) {
            sortingThread.interrupt(); // 停止排序线程
        }
    }

    public void continueDrawing() {
        allTopologicalSorts.clear();
        isGraphConfirmed = false;
        if (sortingThread != null && sortingThread.isAlive()) {
            goingToStop = true;
            allTopologicalSorts.clear();

        }
    }

    // 按钮外观设置方法
    private static void setButtonStyle(JButton button, Color backgroundColor, Color hoverColor, Color textColor) {
        button.setBackground(backgroundColor);
        button.setForeground(textColor);
        button.setFont(new Font("宋体", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(130, 35));

        // 圆角效果
        button.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 鼠标悬停时颜色变化
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(backgroundColor);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Interactive Graph Builder");
            InteractiveGraphBuilder panel = new InteractiveGraphBuilder();
            frame.add(panel);
            frame.setSize(800, 600);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);

            // 设置窗口图标
            Toolkit tk=Toolkit.getDefaultToolkit();
            Image image=tk.createImage("E:\\Project\\TopologicalSortVisualizer\\main\\resources\\icon.jpg"); /*image.gif是你的图标*/
            frame.setIconImage(image);


            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
            buttonPanel.setBackground(new Color(245, 245, 245));
            JButton sortButton = new JButton("执行拓扑排序");
            setButtonStyle(sortButton, new Color(0, 122, 255), new Color(0, 102, 204), Color.WHITE);
            sortButton.addActionListener(e -> {
                if (panel.isGraphConfirmed) return;
                if(panel.nodes.size() == 0) {
                    JOptionPane.showMessageDialog(null,"请添加节点");
                    return;
                }
                panel.goingToStop = false;
                panel.allTopologicalSorts.clear();
                panel.isGraphConfirmed = true;
                panel.findAllTopologicalSorts();
            });
            buttonPanel.add(sortButton);

            JButton clearButton = new JButton("清空图");
            setButtonStyle(clearButton, new Color(255, 99, 71), new Color(204, 51, 51), Color.WHITE);
            clearButton.addActionListener(e -> {
                panel.continueDrawing();
                panel.clearGraph();
                panel.isGraphConfirmed = false;
                panel.goingToStop = false;
            });
            buttonPanel.add(clearButton);

            JButton keepButton = new JButton("继续画图");
            setButtonStyle(keepButton, new Color(34, 139, 34), new Color(28, 120, 28), Color.WHITE);

            keepButton.addActionListener(e -> {
                if (panel.sortingThread.isAlive()) {return;}
                panel.goingToStop = false;
                panel.continueDrawing();
            });
            buttonPanel.add(keepButton);
            frame.add(buttonPanel, BorderLayout.SOUTH);
        });
    }
}
