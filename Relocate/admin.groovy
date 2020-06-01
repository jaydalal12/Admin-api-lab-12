import org.openspaces.admin.*;
import org.openspaces.admin.gsa.*;
import org.openspaces.admin.gsc.*;
import org.openspaces.admin.pu.*;
import java.util.concurrent.TimeUnit;

int delay = 20000;
Double heapSizecheck = Double.parseDouble(System.getenv("heap_size_check"))
int maxTimeToCheck = Integer.parseInt(System.getenv("max_time_to_check"))

AdminFactory factory = new AdminFactory();
factory.addGroup('xap-15.2.0');
Admin admin = factory.createAdmin();

System.out.println("Get GSAs");
GridServiceAgent gsas = admin.getGridServiceAgents().waitForAtLeastOne() ;
// System.out.println("Amount of running GSAs ... " + gsas.class.getName());

GridServiceContainers gscs = admin.getGridServiceContainers();
gscs.waitFor(4, 2, TimeUnit.SECONDS) ;
int numberOfGSCs = gscs.getSize();
int times=0;
Set<String> newGscIds = new HashSet<>();

while ( times < maxTimeToCheck ) {
	times++;
	System.out.println("******************************************");
	System.out.println("Running " + times + " time out of "+maxTimeToCheck);
	GridServiceContainer[] containers = gscs.getContainers();
	boolean extensiveHeapSize=false;
	boolean relocated=false;
	
	for (int cnt=0; cnt < containers.length ; cnt++) {
		System.out.println("containers " + (cnt+1) + " UID is " + containers[cnt].getUid());
		double heapSize = containers[cnt].getVirtualMachine().getStatistics().getMemoryHeapUsedInMB();
		System.out.println("Used Heap Size: " + heapSize  );
		if (heapSize > heapSizecheck && !newGscIds.contains(containers[cnt].getId()) ) {
			System.out.println("try to relocate PU instance to release some memory" );
			containers[cnt].waitFor(1,4,TimeUnit.SECONDS);
			ProcessingUnitInstance[] puis = containers[cnt].getProcessingUnitInstances();
			if (puis.length >= 1)  {
				extensiveHeapSize=true;
				relocated=false;
				System.out.println("There is "+puis.length+" PU in this GSC Look for Another Candidate GSC for relocation of the PU. ");			GridServiceContainer[] tmpcontainers = gscs.getContainers();
	
				for (int lookup=0; lookup < tmpcontainers.length ; lookup++) {
					tmpcontainers[lookup].waitFor(1,4,TimeUnit.SECONDS);
					ProcessingUnitInstance[] mypuis = tmpcontainers[lookup].getProcessingUnitInstances();
					if (mypuis.length == 0) {
						System.out.println("Found Candidate GSC Container for Relocation. Attempting to Relocate first PU to move it to another GSC "+ containers[lookup].getId());
						puis[0].relocate( tmpcontainers[lookup] );
						newGscIds.add(containers[lookup].getId());
						try {
							if(!newGscIds.contains(containers[cnt].getId())){
								containers[cnt].kill();
							}
						}catch(Exception e){
						}
							relocated=true;
						break;				
					}
				}
			}
		}
	}
	if (extensiveHeapSize) {
		if (!relocated) {
			System.out.println("No Available GSC were found for relocation, start a new one.");
			GridServiceContainerOptions gscOptions = new GridServiceContainerOptions();
			gscOptions.vmInputArgument("-Dcom.gs.zones=AdminApiZone");
			gscOptions.vmInputArgument("-Xmx256m");
			gscOptions.vmInputArgument("-Xms256m");
			gsas.startGridService(gscOptions);
		}
		extensiveHeapSize=false;
	} else {
		System.out.println("Current system state does not require new GSC");
	}
	System.out.println("******************************************");
	Thread.sleep(delay);
}

admin.close();
