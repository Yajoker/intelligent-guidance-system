import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.plugin.RankingElementColorTransformer;
import org.gephi.appearance.plugin.RankingNodeSizeTransformer;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.types.EdgeColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.statistics.plugin.GraphDistance;
import uk.ac.ox.oii.sigmaexporter.SigmaExporter;
import uk.ac.ox.oii.sigmaexporter.model.ConfigFile;
import org.openide.util.Lookup;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HeadlessSimple {
    public void script() {
        // 初始化工作空间
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        // 获取图模型、导入控制器等组件
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        Container container;

        // 导入图文件
        try {
            File graphFile = new File("E:\\毕设\\4月\\Gephi_maven\\data\\output.csv");
            container = importController.importFile(graphFile);
            container.getLoader().setEdgeDefault(EdgeDirectionDefault.DIRECTED); // 设置边为有向
            importController.process(container, new DefaultProcessor(), workspace); // 处理导入的文件
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        // 创建节点ID到标签的映射
        Map<String, String> nodeIdToLabelMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("E:\\毕设\\4月\\Gephi_maven\\data\\updated_enhanced_output.csv"))) {
            br.readLine(); // 跳过标题行
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) { // 确保有足够的数据
                    String sourceId = parts[0]; // 源节点ID
                    String label = parts[3]; // 标签
                    nodeIdToLabelMap.put(sourceId, label); // 存储映射
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 应用标签到每个节点
        graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
        Graph graph = graphModel.getGraph();
        for (Node node : graph.getNodes()) {
            String nodeId = node.getId().toString();
            if (nodeIdToLabelMap.containsKey(nodeId)) {
                String label = nodeIdToLabelMap.get(nodeId);
                node.setLabel(label); // 设置节点标签
            }
        }

        // 打印节点ID和标签，检查导入是否成功
        for (Node node : graph.getNodes()) {
            System.out.println("Node ID: " + node.getId() + ", Label: " + node.getLabel());
        }

        importController.process(container, new DefaultProcessor(), workspace);

        // 打印节点和边的数量
        graph = graphModel.getDirectedGraph();
        System.out.println("Nodes: " + graph.getNodeCount());
        System.out.println("Edges: " + graph.getEdgeCount());

        // 过滤器设置：仅保留度数大于1的节点
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        DegreeRangeFilter degreeFilter = new DegreeRangeFilter();
        degreeFilter.init(graph);
        degreeFilter.setRange(new Range(1, Integer.MAX_VALUE));
        Query query = filterController.createQuery(degreeFilter);
        GraphView view = filterController.filter(query);
        graphModel.setVisibleView(view);

        // 使用YifanHu布局算法进行布局
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.initAlgo();
        for (int i = 0; i < 100 && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();

        // 计算图的中心性指标
        GraphDistance distance = new GraphDistance();
        distance.setDirected(true);
        distance.execute(graphModel);

        // 设置节点的大小和颜色
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel(workspace);

        Column centralityColumn = graphModel.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
        Function centralityRanking = appearanceModel.getNodeFunction(centralityColumn, RankingNodeSizeTransformer.class);
        if (centralityRanking != null) {
            RankingNodeSizeTransformer sizeTransformer = (RankingNodeSizeTransformer) centralityRanking.getTransformer();
            sizeTransformer.setMinSize(3);
            sizeTransformer.setMaxSize(10);
            appearanceController.transform(centralityRanking);
        }

        Function colorRanking = appearanceModel.getNodeFunction(centralityColumn, RankingElementColorTransformer.class);
        if (colorRanking != null) {
            RankingElementColorTransformer colorTransformer = (RankingElementColorTransformer) colorRanking.getTransformer();
            colorTransformer.setColors(new Color[]{
                    new Color(0x26C6DA), // 青色
                    new Color(0x283593)  // 深蓝色
            });
            colorTransformer.setColorPositions(new float[]{0f, 1f});
            appearanceController.transform(colorRanking);
        }

        // 预览设置
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel(workspace);
        model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.GRAY));
        model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, new Float(0.1f));
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));

        // 导出为PDF文件
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
            ec.exportFile(new File("E:\\毕设\\4月\\Gephi_maven\\output\\headless_simple.pdf"));
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        // 导出为Web格式
        String outputPath = "E:\\毕设\\4月\\Gephi_maven\\output\\visualOutWeb";
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();  // 创建输出目录
        }

        SigmaExporter se = new SigmaExporter();
        se.setWorkspace(workspace);

        ConfigFile cf = new ConfigFile();
        cf.setDefaults();
        se.setConfigFile(cf, outputPath, false);

        try {
            se.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 导出为JSON格式
        exportToJson(workspace);
    }

    private void exportToJson(Workspace workspace) {
        ExportController exportController = Lookup.getDefault().lookup(ExportController.class);
        GraphExporter exporter = (GraphExporter) exportController.getExporter("json"); // 获取JSON导出器
        exporter.setWorkspace(workspace);

        try {
            File path = new File("E:\\毕设\\4月\\Gephi_maven\\output\\network.json");
            if (!path.getParentFile().exists()) {
                path.getParentFile().mkdirs();  // 确保目录存在
            }
            exportController.exportFile(path, exporter); // 导出文件
        } catch (IOException e) {
            System.err.println("Error exporting to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new HeadlessSimple().script(); // 运行脚本
    }
}
