package edu.unc.mapseq.workflow.gs.mergevc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.renci.jlrm.condor.CondorJob;
import org.renci.jlrm.condor.CondorJobBuilder;
import org.renci.jlrm.condor.CondorJobEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.commons.gs.mergevc.RegisterToIRODSRunnable;
import edu.unc.mapseq.dao.MaPSeqDAOBeanService;
import edu.unc.mapseq.dao.model.Attribute;
import edu.unc.mapseq.dao.model.MimeType;
import edu.unc.mapseq.dao.model.Sample;
import edu.unc.mapseq.dao.model.Workflow;
import edu.unc.mapseq.dao.model.WorkflowRun;
import edu.unc.mapseq.dao.model.WorkflowRunAttempt;
import edu.unc.mapseq.module.core.RemoveCLI;
import edu.unc.mapseq.module.core.ZipCLI;
import edu.unc.mapseq.module.sequencing.freebayes.FreeBayesCLI;
import edu.unc.mapseq.module.sequencing.gatk.GATKPhoneHomeType;
import edu.unc.mapseq.module.sequencing.gatk3.GATKVariantAnnotatorCLI;
import edu.unc.mapseq.module.sequencing.picard.PicardSortVCFCLI;
import edu.unc.mapseq.module.sequencing.picard2.PicardCollectHsMetricsCLI;
import edu.unc.mapseq.module.sequencing.picard2.PicardMarkDuplicates;
import edu.unc.mapseq.module.sequencing.picard2.PicardMergeSAMCLI;
import edu.unc.mapseq.module.sequencing.samtools.SAMToolsDepthCLI;
import edu.unc.mapseq.module.sequencing.samtools.SAMToolsIndexCLI;
import edu.unc.mapseq.workflow.WorkflowException;
import edu.unc.mapseq.workflow.sequencing.AbstractSequencingWorkflow;
import edu.unc.mapseq.workflow.sequencing.SequencingWorkflowJobFactory;
import edu.unc.mapseq.workflow.sequencing.SequencingWorkflowUtil;

public class GSMergeVCWorkflow extends AbstractSequencingWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(GSMergeVCWorkflow.class);

    public GSMergeVCWorkflow() {
        super();
    }

    @Override
    public Graph<CondorJob, CondorJobEdge> createGraph() throws WorkflowException {
        logger.info("ENTERING createGraph()");

        DirectedGraph<CondorJob, CondorJobEdge> graph = new DefaultDirectedGraph<CondorJob, CondorJobEdge>(CondorJobEdge.class);

        int count = 0;

        Set<Sample> sampleSet = SequencingWorkflowUtil.getAggregatedSamples(getWorkflowBeanService().getMaPSeqDAOBeanService(),
                getWorkflowRunAttempt());
        logger.info("sampleSet.size(): {}", sampleSet.size());

        String siteName = getWorkflowBeanService().getAttributes().get("siteName");
        String subjectMergeHome = getWorkflowBeanService().getAttributes().get("subjectMergeHome");
        String referenceSequence = getWorkflowBeanService().getAttributes().get("referenceSequence");
        String baitIntervalList = getWorkflowBeanService().getAttributes().get("baitIntervalList");
        String targetIntervalList = getWorkflowBeanService().getAttributes().get("targetIntervalList");
        String globalBed = getWorkflowBeanService().getAttributes().get("globalBed");
        String localBed = getWorkflowBeanService().getAttributes().get("localBed");

        WorkflowRunAttempt attempt = getWorkflowRunAttempt();
        WorkflowRun workflowRun = attempt.getWorkflowRun();

        try {

            Set<String> subjectNameSet = new HashSet<String>();
            for (Sample sample : sampleSet) {

                if ("Undetermined".equals(sample.getBarcode())) {
                    continue;
                }

                logger.info(sample.toString());

                Set<Attribute> sampleAttributes = sample.getAttributes();
                if (sampleAttributes != null && !sampleAttributes.isEmpty()) {
                    for (Attribute attribute : sampleAttributes) {
                        if (attribute.getName().equals("subjectName")) {
                            subjectNameSet.add(attribute.getValue());
                            break;
                        }
                    }
                }
            }

            Set<String> synchronizedSubjectNameSet = Collections.synchronizedSet(subjectNameSet);

            if (synchronizedSubjectNameSet.isEmpty()) {
                throw new WorkflowException("subjectNameSet is empty");
            }

            if (synchronizedSubjectNameSet.size() > 1) {
                throw new WorkflowException("multiple subjectName values across samples");
            }

            String subjectName = synchronizedSubjectNameSet.iterator().next();

            if (StringUtils.isEmpty(subjectName)) {
                throw new WorkflowException("empty subjectName");
            }

            // project directories
            File subjectDirectory = new File(subjectMergeHome, subjectName);
            subjectDirectory.mkdirs();
            logger.info("subjectDirectory.getAbsolutePath(): {}", subjectDirectory.getAbsolutePath());

            List<File> bamFileList = new ArrayList<File>();

            for (Sample sample : sampleSet) {

                if ("Undetermined".equals(sample.getBarcode())) {
                    continue;
                }

                Workflow workflow = getWorkflowBeanService().getMaPSeqDAOBeanService().getWorkflowDAO().findByName("GSAlignment").get(0);

                File bamFile = SequencingWorkflowUtil.findFile(getWorkflowBeanService().getMaPSeqDAOBeanService(), sample, workflowRun, workflow,
                        PicardMarkDuplicates.class, MimeType.APPLICATION_BAM, ".rg.md.bam");

                if (bamFile == null) {
                    logger.warn("No BAM File found for: {}", sample.toString());
                    continue;
                }

                bamFileList.add(bamFile);

            }

            // what do we expect here? Number of samples should match number of bam file found, right?
            if (sampleSet.size() != bamFileList.size()) {
                throw new WorkflowException("Number of Samples does not match number of bam files found");
            }

            // new job
            CondorJobBuilder builder = SequencingWorkflowJobFactory.createJob(++count, PicardMergeSAMCLI.class, attempt.getId()).siteName(siteName);
            File mergeBAMFilesOut = new File(subjectDirectory, String.format("%s.merged.bam", subjectName));
            builder.addArgument(PicardMergeSAMCLI.SORTORDER, "coordinate").addArgument(PicardMergeSAMCLI.OUTPUT, mergeBAMFilesOut.getAbsolutePath());
            for (File f : bamFileList) {
                logger.info("Using file: {}", f.getAbsolutePath());
                builder.addArgument(PicardMergeSAMCLI.INPUT, f.getAbsolutePath());
            }
            CondorJob mergeBAMFilesJob = builder.build();
            logger.info(mergeBAMFilesJob.toString());
            graph.addVertex(mergeBAMFilesJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, SAMToolsIndexCLI.class, attempt.getId()).siteName(siteName);
            File mergeBAMFilesIndexOut = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".bai"));
            builder.addArgument(SAMToolsIndexCLI.INPUT, mergeBAMFilesOut.getAbsolutePath()).addArgument(SAMToolsIndexCLI.OUTPUT,
                    mergeBAMFilesIndexOut.getAbsolutePath());
            CondorJob samtoolsIndexJob = builder.build();
            logger.info(samtoolsIndexJob.toString());
            graph.addVertex(samtoolsIndexJob);
            graph.addEdge(mergeBAMFilesJob, samtoolsIndexJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, ZipCLI.class, attempt.getId()).siteName(siteName);
            File zipOut = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".zip"));
            builder.addArgument(ZipCLI.WORKDIR, subjectDirectory.getAbsolutePath()).addArgument(ZipCLI.ENTRY, mergeBAMFilesOut.getAbsolutePath())
                    .addArgument(ZipCLI.ENTRY, mergeBAMFilesIndexOut.getAbsolutePath()).addArgument(ZipCLI.OUTPUT, zipOut.getAbsolutePath());
            CondorJob zipJob = builder.build();
            logger.info(zipJob.toString());
            graph.addVertex(zipJob);
            graph.addEdge(samtoolsIndexJob, zipJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, SAMToolsDepthCLI.class, attempt.getId()).siteName(siteName);
            File mergeBAMFilesDepthOut = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".depth.txt"));
            builder.addArgument(SAMToolsDepthCLI.BAM, mergeBAMFilesOut.getAbsolutePath())
                    .addArgument(SAMToolsIndexCLI.OUTPUT, mergeBAMFilesDepthOut.getAbsolutePath()).addArgument(SAMToolsDepthCLI.BED, localBed)
                    .addArgument(SAMToolsDepthCLI.BASEQUALITY, 20).addArgument(SAMToolsDepthCLI.MAPPINGQUALITY, 20);
            CondorJob samtoolsDepthJob = builder.build();
            logger.info(samtoolsDepthJob.toString());
            graph.addVertex(samtoolsDepthJob);
            graph.addEdge(samtoolsIndexJob, samtoolsDepthJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, PicardCollectHsMetricsCLI.class, attempt.getId()).siteName(siteName);
            File picardCollectHsMetricsFile = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".hs.metrics"));
            builder.addArgument(PicardCollectHsMetricsCLI.INPUT, mergeBAMFilesOut.getAbsolutePath())
                    .addArgument(PicardCollectHsMetricsCLI.OUTPUT, picardCollectHsMetricsFile.getAbsolutePath())
                    .addArgument(PicardCollectHsMetricsCLI.REFERENCESEQUENCE, referenceSequence)
                    .addArgument(PicardCollectHsMetricsCLI.BAITINTERVALS, baitIntervalList)
                    .addArgument(PicardCollectHsMetricsCLI.TARGETINTERVALS, targetIntervalList);
            CondorJob picardCollectHsMetricsJob = builder.build();
            logger.info(picardCollectHsMetricsJob.toString());
            graph.addVertex(picardCollectHsMetricsJob);
            graph.addEdge(samtoolsIndexJob, picardCollectHsMetricsJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, FreeBayesCLI.class, attempt.getId()).siteName(siteName);
            File freeBayesOutput = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".fb.vcf"));
            builder.addArgument(FreeBayesCLI.GENOTYPEQUALITIES).addArgument(FreeBayesCLI.REPORTMONOMORPHIC)
                    .addArgument(FreeBayesCLI.BAM, mergeBAMFilesOut.getAbsolutePath())
                    .addArgument(FreeBayesCLI.VCF, freeBayesOutput.getAbsolutePath()).addArgument(FreeBayesCLI.FASTAREFERENCE, referenceSequence)
                    .addArgument(FreeBayesCLI.TARGETS, globalBed);
            CondorJob freeBayesVCFJob = builder.build();
            logger.info(freeBayesVCFJob.toString());
            graph.addVertex(freeBayesVCFJob);
            graph.addEdge(samtoolsIndexJob, freeBayesVCFJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, PicardSortVCFCLI.class, attempt.getId()).siteName(siteName);
            File picardSortVCFOutput = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".fb.ps.vcf"));
            builder.addArgument(PicardSortVCFCLI.INPUT, freeBayesOutput.getAbsolutePath()).addArgument(PicardSortVCFCLI.OUTPUT,
                    picardSortVCFOutput.getAbsolutePath());
            CondorJob picardSortVCFJob = builder.build();
            logger.info(picardSortVCFJob.toString());
            graph.addVertex(picardSortVCFJob);
            graph.addEdge(freeBayesVCFJob, picardSortVCFJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, GATKVariantAnnotatorCLI.class, attempt.getId()).siteName(siteName);
            File gatkVariantAnnotatorOutput = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".fb.ps.va.vcf"));
            builder.addArgument(GATKVariantAnnotatorCLI.VCF, picardSortVCFOutput.getAbsolutePath())
                    .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "FisherStrand").addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "QualByDepth")
                    .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "ReadPosRankSumTest")
                    .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "DepthPerAlleleBySample")
                    .addArgument(GATKVariantAnnotatorCLI.ANNOTATION, "HomopolymerRun")
                    .addArgument(GATKVariantAnnotatorCLI.BAM, mergeBAMFilesOut.getAbsolutePath())
                    .addArgument(GATKVariantAnnotatorCLI.REFERENCESEQUENCE, referenceSequence)
                    .addArgument(GATKVariantAnnotatorCLI.OUT, gatkVariantAnnotatorOutput.getAbsolutePath())
                    .addArgument(GATKVariantAnnotatorCLI.PHONEHOME, GATKPhoneHomeType.NO_ET.toString());
            CondorJob gatkVCFJob = builder.build();
            logger.info(gatkVCFJob.toString());
            graph.addVertex(gatkVCFJob);
            graph.addEdge(picardSortVCFJob, gatkVCFJob);

            // new job
            builder = SequencingWorkflowJobFactory.createJob(++count, RemoveCLI.class, attempt.getId()).siteName(siteName);
            builder.addArgument(RemoveCLI.FILE, freeBayesOutput.getAbsolutePath()).addArgument(RemoveCLI.FILE, picardSortVCFOutput.getAbsolutePath());
            CondorJob removeJob = builder.build();
            logger.info(removeJob.toString());
            graph.addVertex(removeJob);
            graph.addEdge(gatkVCFJob, removeJob);

        } catch (Exception e) {
            throw new WorkflowException(e);
        }

        return graph;
    }

    @Override
    public void postRun() throws WorkflowException {
        logger.info("ENTERING postRun()");

        ExecutorService es = Executors.newSingleThreadExecutor();

        try {

            MaPSeqDAOBeanService daoBean = getWorkflowBeanService().getMaPSeqDAOBeanService();
            RegisterToIRODSRunnable registerToIRODSRunnable = new RegisterToIRODSRunnable(daoBean, getWorkflowRunAttempt());
            es.submit(registerToIRODSRunnable);

            es.shutdown();
            es.awaitTermination(1L, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
            throw new WorkflowException(e);
        }

    }

}
