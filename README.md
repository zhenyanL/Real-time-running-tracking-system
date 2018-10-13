
##Service Start Sequence
1. docker-compose up
2. sh ./start-eruka.sh
3. sh ./start-hystrix.sh
4. sh ./start-location-simulator.sh
5. sh ./start-location-distribution.sh
6. sh ./start-location-updater.sh

##UI
1. Open Simulator UI on http://localhost:9005
2. Click run simulation
3. Click cancel if you want to cancel simulation

##Hystrix stream is open at
http://localhost:9005/hystrix.stream
