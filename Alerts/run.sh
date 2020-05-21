#!/usr/bin/env bash
java -cp "$XAPHOMEDIR/lib/required/*":"target/*" com.gs.admin.monitor.AdminAlert $*
