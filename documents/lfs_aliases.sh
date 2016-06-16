#!/bin/bash
# bash_aliases
#datename=catalina.out.$(date +%Y%m%d-%H_%M_%S);

echo " Add some aliases !!!";


alias zzztest="bakname=${bakname=catalina.out.\$(date +%Y%m%d-%H_%M_%S).log};echo 'cp logs/catalina.out logs/$bakname at \`date\`'; echo \"aaa$bakname\" ";

alias lessg="less logs/catalina.out";

alias tailg="tail -f logs/catalina.out";

alias baklg="bakname=${bakname=catalina.out.\$(date +%Y%m%d-%H_%M_%S).log};echo \"cp logs/catalina.out logs/$bakname at \`date\`\";cp logs/catalina.out logs/$bakname";

alias clelg="baklg; echo \"clear logs/catalina.out at \`date\`\" ; echo \"clear logs/catalina.out at \`date\`\">logs/catalina.out ";

alias clelg_no_bak="echo \"clelg_no_bak at \`date\` >logs/catalina.out\";\"clelg_no_bak at \`date\`\">logs/catalina.out ";

#alias clelg_all_logs=" ";

alias clewlg="echo \"rm -r `pwd`/work/Catalina/localhost/_/  at \`date\`\";rm -r work/Catalina/localhost/_/ ";

alias pst="ps -ef | grep tomcat-";

#alias rstall=" if( \`pwd\` != '/usr/linkapp/bin/') {echo 'rstall is not allowed here '};. /usr/linkapp/bin/tomcat-master/bin/rstmst.sh;. /usr/linkapp/bin/tomcat-nas/bin/rstns.sh";


#alias rstall=". /usr/linkapp/bin/tomcat-master/bin/rstall.sh";
alias rstall=". /bin/rstall.sh";

alias rstmst=./bin/rstmst.sh;

alias rstns=./bin/rstns.sh;

alias rs="kill -9 \`ps -ef|grep -w 'tomcat' |grep -v grep|awk '{print $2}'\` ; cd /usr/linkapp/bin/tomcat-master; ./bin/startup.sh ;  tail -f logs/catalina.out "