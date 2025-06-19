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
import org.openide.util.Lookup;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class HeadlessSimple {
    public void script() {
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);

        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        Container container;
        try {
            File file = new File("E:\\毕设\\4月\\Gephi_maven\\data\\output.csv");
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDirectionDefault.DIRECTED);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        importController.process(container, new DefaultProcessor(), workspace);

        DirectedGraph graph = graphModel.getDirectedGraph();
        System.out.println("Nodes: " + graph.getNodeCount());
        System.out.println("Edges: " + graph.getEdgeCount());

        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        DegreeRangeFilter degreeFilter = new DegreeRangeFilter();
        degreeFilter.init(graph);
        degreeFilter.setRange(new Range(3, Integer.MAX_VALUE));
        Query query = filterController.createQuery(degreeFilter);
        GraphView view = filterController.filter(query);
        graphModel.setVisibleView(view);

        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.initAlgo();
        for (int i = 0; i < 100 && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();

        GraphDistance distance = new GraphDistance();
        distance.setDirected(true);
        distance.execute(graphModel);

        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel(workspace);

        // 对于大小和颜色的设置，我们使用图的中心性指标
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
            colorTransformer.setColors(new Color[]{new Color(0xFEF0D9), new Color(0xB30000)});
            colorTransformer.setColorPositions(new float[]{0f, 1f});
            appearanceController.transform(colorRanking);
        }

        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel(workspace);
        model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.GRAY));
        model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, new Float(0.1f));
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));

        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
            ec.exportFile(new File("headless_simple.pdf"));
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    public static void main(String[] args) {
        new HeadlessSimple().script();
    }
}
