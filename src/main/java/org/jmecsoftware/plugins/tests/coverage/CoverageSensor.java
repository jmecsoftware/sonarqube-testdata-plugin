package org.jmecsoftware.plugins.tests.coverage;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.jmecsoftware.plugins.tests.TestDataImporterPlugin;

import org.jmecsoftware.plugins.tests.utils.ReportUtils;

public class CoverageSensor implements Sensor {

  private final CoverageCache cache;
  private static final Logger LOG = Loggers.get(CoverageSensor.class);
 
  private final Settings settings;

  public CoverageSensor(Settings settings, CoverageCache cache) {
    this.settings = settings;
    this.cache = cache;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Unit Test Coverage Report")
      .requireProperty(TestDataImporterPlugin.COV_REPORT_PATHS_KEY);
  }

  @Override
  public void execute(SensorContext context) {
    
    // create cache of data
    if(this.cache.isEmpty()) {
      List<File> reports = ReportUtils.getReports(settings, context.fileSystem().baseDir(), TestDataImporterPlugin.COV_REPORT_PATHS_KEY);
      if (!reports.isEmpty()) {
        for(File reportFile : reports) {
          LOG.info("Parsing {}", reportFile);
          CoverageReportParser parser = new CoverageReportParser(this.cache);
          parser.parse(reportFile);
        }      
      }    
    } else {
      LOG.info("Cache Has Been Creeted No Parsing");
    }
    
    saveMeasures(context, this.cache.coverageCache());
  }

  private void saveMeasures(SensorContext context,
    Map<String, CoverageMeasures> coverageMeasures) {
    for (Map.Entry<String, CoverageMeasures> entry : coverageMeasures.entrySet()) {
      String filePath = entry.getKey();
      InputFile inputFile = context.fileSystem().inputFile(context.fileSystem().predicates().hasPath(filePath));
      if (inputFile != null) {        
        NewCoverage newCoverage = context.newCoverage().onFile(inputFile);      
        Collection<CoverageMeasure> measures = entry.getValue().getCoverageMeasures();
        LOG.debug("Saving '{}' coverage measures for file '{}'", measures.size(), filePath);
        for (CoverageMeasure measure : measures) {
          try
          {
            newCoverage.lineHits(measure.getLine(), measure.getHits());
          } catch(Exception ex) {
            LOG.error("Cannot save Line Hits for Line '{}' '{}' : '{}', ignoring measure", filePath, measure.getLine(), ex.getMessage());
          }            
          
          if (measure.getConditions() > 0) {
            try
            {
              newCoverage.conditions(measure.getLine(), measure.getConditions(), measure.getCoveredConditions());
            } catch(Exception ex) {
              LOG.error("Cannot save Conditions Hits for Line '{}' '{}' : '{}', ignoring measure", filePath, measure.getLine(), ex.getMessage());
            }                                   
          }
        }
        
        try
        {
          newCoverage.save();
        } catch(Exception ex) {
          LOG.error("Cannot save measure '{}' : '{}', ignoring measure", filePath, ex.getMessage());
        }        
      }       
    }
  }  
}