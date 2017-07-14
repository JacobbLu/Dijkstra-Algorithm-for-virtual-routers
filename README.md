# Dijkstra-Algorithm-for-virtual-routers

1. Type "make" to compile
2. Run the nse first on the host hostX:
	"./nse-linux386 hostY 9999"
3. Run router 1-5 on the host hostY:
	"./router.sh 1 hostX 9999 9991"
	"./router.sh 2 hostX 9999 9991"
	"./router.sh 3 hostX 9999 9991"
	"./router.sh 4 hostX 9999 9991"
	"./router/sh 5 hostX 9999 9991"
4. The program has been tested on hosts:
	hostX: ubuntu1204-006
	hostY: ubuntu1204-004
5. Type "make clean" to remove *.class and *.log files
