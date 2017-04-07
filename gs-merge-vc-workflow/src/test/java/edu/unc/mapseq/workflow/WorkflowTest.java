package edu.unc.mapseq.workflow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.Test;
import org.renci.jlrm.condor.CondorJob;
import org.renci.jlrm.condor.CondorJobBuilder;
import org.renci.jlrm.condor.CondorJobEdge;
import org.renci.jlrm.condor.ext.CondorDOTExporter;

import edu.unc.mapseq.module.core.RemoveCLI;
import edu.unc.mapseq.module.core.ZipCLI;
import edu.unc.mapseq.module.sequencing.freebayes.FreeBayesCLI;
import edu.unc.mapseq.module.sequencing.gatk.GATKPhoneHomeType;
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
        File mergeBAMFilesOut = new File(String.format("%s.merged.bam", "<subjectName>"));
        CondorJob mergeBAMFilesJob = new CondorJobBuilder().name(String.format("%s_%d", PicardMergeSAMCLI.class.getSimpleName(), ++count))
                .addArgument(PicardMergeSAMCLI.SORTORDER, "coordinate").addArgument(PicardMergeSAMCLI.OUTPUT, mergeBAMFilesOut.getName())
                .addArgument(PicardMergeSAMCLI.INPUT, "<input>.bam").build();
        graph.addVertex(mergeBAMFilesJob);

        // new job
        File mergeBAMFilesIndexOut = new File(mergeBAMFilesOut.getName().replace(".bam", ".bai"));
        CondorJob samtoolsIndexJob = new CondorJobBuilder().name(String.format("%s_%d", SAMToolsIndexCLI.class.getSimpleName(), ++count))
                .addArgument(SAMToolsIndexCLI.INPUT, mergeBAMFilesOut.getName()).addArgument(SAMToolsIndexCLI.OUTPUT, mergeBAMFilesIndexOut.getName())
                .build();
        graph.addVertex(samtoolsIndexJob);
        graph.addEdge(mergeBAMFilesJob, samtoolsIndexJob);

        // new job
        File zipOut = new File(mergeBAMFilesOut.getName().replace(".bam", ".zip"));
        CondorJob zipJob = new CondorJobBuilder().name(String.format("%s_%d", ZipCLI.class.getSimpleName(), ++count))
                .addArgument(ZipCLI.WORKDIR, "<workdirectory>").addArgument(ZipCLI.ENTRY, mergeBAMFilesOut.getName())
                .addArgument(ZipCLI.ENTRY, mergeBAMFilesIndexOut.getName()).addArgument(ZipCLI.OUTPUT, zipOut.getName()).build();
        graph.addVertex(zipJob);
        graph.addEdge(samtoolsIndexJob, zipJob);

        // new job
        File mergeBAMFilesDepthOut = new File(mergeBAMFilesOut.getName().replace(".bam", ".depth.txt"));
        CondorJob samtoolsDepthJob = new CondorJobBuilder().name(String.format("%s_%d", SAMToolsDepthCLI.class.getSimpleName(), ++count))
                .addArgument(SAMToolsDepthCLI.BAM, mergeBAMFilesOut.getName()).addArgument(SAMToolsIndexCLI.OUTPUT, mergeBAMFilesDepthOut.getName())
                .addArgument(SAMToolsDepthCLI.BED, "<localBed>").addArgument(SAMToolsDepthCLI.BASEQUALITY, 20)
                .addArgument(SAMToolsDepthCLI.MAPPINGQUALITY, 20).build();
        graph.addVertex(samtoolsDepthJob);
        graph.addEdge(samtoolsIndexJob, samtoolsDepthJob);

        // new job
        File picardCollectHsMetricsFile = new File(mergeBAMFilesOut.getName().replace(".bam", ".hs.metrics"));
        CondorJob picardCollectHsMetricsJob = new CondorJobBuilder()
                .name(String.format("%s_%d", PicardCollectHsMetricsCLI.class.getSimpleName(), ++count))
                .addArgument(PicardCollectHsMetricsCLI.INPUT, mergeBAMFilesOut.getName())
                .addArgument(PicardCollectHsMetricsCLI.OUTPUT, picardCollectHsMetricsFile.getName())
                .addArgument(PicardCollectHsMetricsCLI.REFERENCESEQUENCE, "<referenceSequence>")
                .addArgument(PicardCollectHsMetricsCLI.BAITINTERVALS, "<baitIntervalList>")
                .addArgument(PicardCollectHsMetricsCLI.TARGETINTERVALS, "<targetIntervalList>").build();
        graph.addVertex(picardCollectHsMetricsJob);
        graph.addEdge(samtoolsIndexJob, picardCollectHsMetricsJob);

        // new job
        File freeBayesOutput = new File(mergeBAMFilesOut.getName().replace(".bam", ".fb.vcf"));
        CondorJob freeBayesVCFJob = new CondorJobBuilder().name(String.format("%s_%d", FreeBayesCLI.class.getSimpleName(), ++count))
                .addArgument(FreeBayesCLI.GENOTYPEQUALITIES).addArgument(FreeBayesCLI.REPORTMONOMORPHIC)
                .addArgument(FreeBayesCLI.BAM, mergeBAMFilesOut.getName()).addArgument(FreeBayesCLI.VCF, freeBayesOutput.getName())
                .addArgument(FreeBayesCLI.FASTAREFERENCE, "<referenceSequence>").addArgument(FreeBayesCLI.TARGETS, "<globalBed>").build();
        graph.addVertex(freeBayesVCFJob);
        graph.addEdge(samtoolsIndexJob, freeBayesVCFJob);

        // new job
        File picardSortVCFOutput = new File(mergeBAMFilesOut.getName().replace(".bam", ".fb.sorted.vcf"));
        CondorJob picardSortVCFJob = new CondorJobBuilder().name(String.format("%s_%d", PicardSortVCFCLI.class.getSimpleName(), ++count))
                .addArgument(PicardSortVCFCLI.INPUT, freeBayesOutput.getName()).addArgument(PicardSortVCFCLI.OUTPUT, picardSortVCFOutput.getName())
                .build();
        graph.addVertex(picardSortVCFJob);
        graph.addEdge(freeBayesVCFJob, picardSortVCFJob);

        // new job
        File gatkVariantAnnotatorOutput = new File(mergeBAMFilesOut.getName().replace(".bam", ".fb.sorted.va.vcf"));
        CondorJob gatkVCFJob = new CondorJobBuilder().name(String.format("%s_%d", GATKVariantAnnotatorCLI.class.getSimpleName(), ++count))
                .addArgument(GATKVariantAnnotatorCLI.VCF, picardSortVCFOutput.getName())
                .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "FisherStrand").addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "QualByDepth")
                .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "ReadPosRankSumTest")
                // .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "DepthPerAlleleBySample")
                .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "HomopolymerRun")
                .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "SpanningDeletions")
                .addArgument(GATKVariantAnnotatorCLI.BAM, mergeBAMFilesOut.getName())
                .addArgument(GATKVariantAnnotatorCLI.REFERENCESEQUENCE, "<referenceSequence>")
                .addArgument(GATKVariantAnnotatorCLI.OUT, gatkVariantAnnotatorOutput.getName())
                .addArgument(GATKVariantAnnotatorCLI.PHONEHOME, GATKPhoneHomeType.NO_ET.toString()).build();
        graph.addVertex(gatkVCFJob);
        graph.addEdge(picardSortVCFJob, gatkVCFJob);

        // new job
        CondorJob removeJob = new CondorJobBuilder().name(String.format("%s_%d", RemoveCLI.class.getSimpleName(), ++count))
                .addArgument(RemoveCLI.FILE, freeBayesOutput.getName()).addArgument(RemoveCLI.FILE, picardSortVCFOutput.getName()).build();
        graph.addVertex(removeJob);
        graph.addEdge(gatkVCFJob, removeJob);

        CondorDOTExporter<CondorJob, CondorJobEdge> dotExporter = new CondorDOTExporter<CondorJob, CondorJobEdge>(a -> a.getName(), b -> {

            StringBuilder sb = new StringBuilder();
            sb.append(b.getName());
            if (StringUtils.isNotEmpty(b.getArgumentsClassAd().getValue())) {
                sb.append("\n");
                Pattern p = Pattern.compile("--\\w+(\\s[a-zA-Z_0-9<>\\.]+)?");
                Matcher m = p.matcher(b.getArgumentsClassAd().getValue());
                while (m.find()) {
                    sb.append(String.format("%s\n", m.group()));
                }
            }

            return sb.toString();

        }, null, null, null, null);
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
