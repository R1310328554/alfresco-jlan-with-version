if [ `pwd` != '/usr/linkapp/bin/tomcat-master' ] 
	then
		echo "rstall is not allowed in current dir : `pwd` !"
		return
fi

./bin/rstmst.sh;../tomcat-nas/bin/rstns.sh