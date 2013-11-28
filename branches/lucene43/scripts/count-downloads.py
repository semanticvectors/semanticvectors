# Handy script for counting the number of times each release of
# semantic vectors has been downloaded.
#
# Run using 'python count-downloads.py'.

import urllib
import re

site_url = "http://code.google.com/p/semanticvectors/downloads/list"

site = urllib.urlopen(site_url)
site_html = site.readlines()

td_match = re.compile("<td class=\"vt col_4\"")
sv_match = re.compile("detail\?name=(semanticvectors[^\&]*)\&")
count_match = re.compile("^\s*(\d+)\s*$")

total_count = 0

for i in range(len(site_html)):
 if td_match.search(site_html[i]):
   if sv_match.search(site_html[i]):
     download_name = sv_match.search(site_html[i]).group(1)
     while True:
       if count_match.search(site_html[i+1]):
         count = count_match.search(site_html[i+1]).group(1)
         total_count += int(count)
         print count, "\t", download_name
         break
       i += 1

print "Total:", total_count
