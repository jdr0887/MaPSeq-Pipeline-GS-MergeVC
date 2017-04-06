package edu.unc.mapseq.workflow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.Test;
import org.renci.jlrm.condor.CondorJob;
import org.renci.jlrm.condor.CondorJobBuilder;
import org.renci.jlrm.condor.CondorJobEdge;
import org.renci.jlrm.condor.ext.CondorDOTExporter;
import org.renci.jlrm.condor.ext.CondorJobVertexNameProvider;

import edu.unc.mapseq.module.core.RemoveCLI;
import edu.unc.mapseq.module.core.ZipCLI;
import edu.unc.mapseq.module.sequencing.freebayes.FreeBayesCLI;
import edu.unc.mapseq.module.sequencing.gatk.GATKVariantAnnotatorCLI;
import edu.unc.mapseq.module.sequencing.picard.PicardSortVCFCLI;
import edu.unc.mapseq.module.sequencing.picard2.PicardCollectHsMetricsCLI;
import edu.unc.mapseq.module.sequencing.picard2.PicardMergeSAMCLI;
import edu.unc.mapseq.module.sequencing.samtools.SAMToolsDepthCLI;
import edu.unc.mapseq.module.sequencing.samtools.SAMToolsIndexCLI;

public class WorkflowTest {

    @Test
    public void createDot() {

        DirectedGraph<CondorJob, CondorJobEdge> graph = new DefaultDirectedGraph<CondorJob, CondorJobEdge>(CondorJobEdge.class);

        int count = 0;

        // new job
        CondorJob mergeBAMFilesJob = new CondorJobBuilder().name(String.format("%s_%d", PicardMergeSAMCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(mergeBAMFilesJob);

        // new job
        CondorJob samtoolsIndexJob = new CondorJobBuilder().name(String.format("%s_%d", SAMToolsIndexCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(samtoolsIndexJob);
        graph.addEdge(mergeBAMFilesJob, samtoolsIndexJob);

        // new job
        CondorJob zipJob = new CondorJobBuilder().name(String.format("%s_%d", ZipCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(zipJob);
        graph.addEdge(samtoolsIndexJob, zipJob);

        // new job
        CondorJob samtoolsDepthJob = new CondorJobBuilder().name(String.format("%s_%d", SAMToolsDepthCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(samtoolsDepthJob);
        graph.addEdge(samtoolsIndexJob, samtoolsDepthJob);

        // new job
        CondorJob picardCollectHsMetricsJob = new CondorJobBuilder()
                .name(String.format("%s_%d", PicardCollectHsMetricsCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(picardCollectHsMetricsJob);
        graph.addEdge(samtoolsIndexJob, picardCollectHsMetricsJob);

        // new job
        CondorJob freeBayesVCFJob = new CondorJobBuilder().name(String.format("%s_%d", FreeBayesCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(freeBayesVCFJob);
        graph.addEdge(samtoolsIndexJob, freeBayesVCFJob);

        // new job
        CondorJob picardSortVCFJob = new CondorJobBuilder().name(String.format("%s_%d", PicardSortVCFCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(picardSortVCFJob);
        graph.addEdge(freeBayesVCFJob, picardSortVCFJob);

        // new job
        CondorJob gatkVCFJob = new CondorJobBuilder().name(String.format("%s_%d", GATKVariantAnnotatorCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(gatkVCFJob);
        graph.addEdge(picardSortVCFJob, gatkVCFJob);

        // new job
        CondorJob removeJob = new CondorJobBuilder().name(String.format("%s_%d", RemoveCLI.class.getSimpleName(), ++count)).build();
        graph.addVertex(removeJob);
        graph.addEdge(gatkVCFJob, removeJob);

        CondorJobVertexNameProvider vnp = new CondorJobVertexNameProvider();
        CondorDOTExporter<CondorJob, CondorJobEdge> dotExporter = new CondorDOTExporter<CondorJob, CondorJobEdge>(vnp, vnp, null, null, null, null);
        File srcSiteResourcesImagesDir = new File("../src/site/resources/images");
        if (!srcSiteResourcesImagesDir.exists()) {
            srcSiteResourcesImagesDir.mkdirs();
        }
        File dotFile = new File(srcSiteResourcesImagesDir, "workflow.dag.dot");
        try {
            FileWriter fw = new FileWriter(dotFile);
            dotExporter.export(fw, graph);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
