ps -ef | grep -E 'tomcat-master|libreoffice'  | grep  -v grep | awk  'BEGIN{print "\n\n\nRestarting MASTER ++++++++++++++++++++\n\n\n"}{ cnt=split($0, arr, " "); for(k=1;k<=cnt;k++){ if(index(arr[k], "grep") > 0){break;};if(index(arr[k], lo) > 0){ print "\nKilling  libreoffice3 --- pid : "$2; system("kill -9 "$2); break;};ind=index(arr[k], ch);if(ind>0){execpath=substr(arr[k],length(ch)+1);print "\nKilling  "execpath" --- pid : "$2; execpath=execpath"/bin/"; system("kill -9 "$2); system("sleep 1"); print "\nStarting  "execpath;system("sh "execpath"startup.sh ");}} } END{print "\n\n\nRestart finished ++++++++++++++++++++\n\n\n"}' ch="-Dcatalina.home=" lo="/opt/libreoffice3.6/program/soffice.bin";