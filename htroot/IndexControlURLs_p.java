// IndexControlRWIs_p.java
// -----------------------
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2007-11-14 01:15:28 +0000 (Mi, 14 Nov 2007) $
// $LastChangedRevision: 4216 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Iterator;

import de.anomic.http.httpRequestHeader;
import de.anomic.index.indexRepositoryReference;
import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRotateIterator;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public class IndexControlURLs_p {
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        final serverObjects prop = new serverObjects();
        prop.put("urlstring", "");
        prop.put("urlhash", "");
        prop.put("result", "");
        prop.put("ucount", Integer.toString(sb.webIndex.countURL()));
        prop.put("otherHosts", "");
        prop.put("genUrlProfile", 0);
        prop.put("statistics", 1);
        prop.put("statistics_lines", 100);
        prop.put("statisticslines", 0);
        prop.put("reload", 0);
        
        // show export messages
        final indexRepositoryReference.Export export = sb.webIndex.exportURL();
        if ((export != null) && (export.isAlive())) {
        	// there is currently a running export
            prop.put("lurlexport", 2);
            prop.put("lurlexportfinished", 0);
    		prop.put("lurlexporterror", 0);
    		prop.put("lurlexport_exportfile", export.file().toString());
            prop.put("lurlexport_urlcount", export.count());
            prop.put("reload", 1);
        } else {
            prop.put("lurlexport", 1);
            prop.put("lurlexport_exportfile", sb.getRootPath() + "/DATA/EXPORT/" + serverDate.formatShortSecond());
            if (export == null) {
                // there has never been an export
                prop.put("lurlexportfinished", 0);
                prop.put("lurlexporterror", 0);
            } else {
                // an export was running but has finished
                prop.put("lurlexportfinished", 1);
                prop.put("lurlexportfinished_exportfile", export.file().toString());
                prop.put("lurlexportfinished_urlcount", export.count());
                if (export.failed() == null) {
                    prop.put("lurlexporterror", 0);
                } else {
                    prop.put("lurlexporterror", 1);
                    prop.put("lurlexporterror_exportfile", export.file().toString());
                    prop.put("lurlexporterror_exportfailmsg", export.failed());
                }
            }
        }
        
        if (post == null || env == null) {
            return prop; // nothing to do
        }
        
        // post values that are set on numerous input fields with same name
        String urlstring = post.get("urlstring", "").trim();
        String urlhash = post.get("urlhash", "").trim();
        
        if (!urlstring.startsWith("http://") &&
            !urlstring.startsWith("https://")) { urlstring = "http://" + urlstring; }

        prop.putHTML("urlstring", urlstring);
        prop.put("urlhash", urlhash);
        prop.put("result", " ");

        if (post.containsKey("urlhashdeleteall")) {
            final int i = sb.removeAllUrlReferences(urlhash, true);
            prop.put("result", "Deleted URL and " + i + " references from " + i + " word indexes.");
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }

        if (post.containsKey("urlhashdelete")) {
            final indexURLReference entry = sb.webIndex.getURL(urlhash, null, 0);
            if (entry == null) {
                prop.put("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
            } else {
                urlstring = entry.comp().url().toNormalform(false, true);
                prop.put("urlstring", "");
                sb.urlRemove(urlhash);
                prop.putHTML("result", "Removed URL " + urlstring);
            }
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }

        if (post.containsKey("urldelete")) {
            try {
                urlhash = (new yacyURL(urlstring, null)).hash();
            } catch (final MalformedURLException e) {
                urlhash = null;
            }
            if ((urlhash == null) || (urlstring == null)) {
                prop.put("result", "No input given; nothing deleted.");
            } else {
                sb.urlRemove(urlhash);
                prop.putHTML("result", "Removed URL " + urlstring);
            }
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }

        if (post.containsKey("urlstringsearch")) {
            try {
                final yacyURL url = new yacyURL(urlstring, null);
                urlhash = url.hash();
                prop.put("urlhash", urlhash);
                final indexURLReference entry = sb.webIndex.getURL(urlhash, null, 0);
                if (entry == null) {
                    prop.putHTML("urlstring", "unknown url: " + urlstring);
                    prop.put("urlhash", "");
                } else {
                    prop.putAll(genUrlProfile(sb, entry, urlhash));
                    prop.put("statistics", 0);
                }
            } catch (final MalformedURLException e) {
                prop.putHTML("urlstring", "bad url: " + urlstring);
                prop.put("urlhash", "");
            }
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }

        if (post.containsKey("urlhashsearch")) {
            final indexURLReference entry = sb.webIndex.getURL(urlhash, null, 0);
            if (entry == null) {
                prop.put("result", "No Entry for URL hash " + urlhash);
            } else {
                prop.putHTML("urlstring", entry.comp().url().toNormalform(false, true));
                prop.putAll(genUrlProfile(sb, entry, urlhash));
                prop.put("statistics", 0);
            }
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }

        // generate list
        if (post.containsKey("urlhashsimilar")) {
            try {
                final Iterator<indexURLReference> entryIt = new kelondroRotateIterator<indexURLReference>(sb.webIndex.entriesURL(true, urlhash), new String(kelondroBase64Order.zero((urlhash == null ? 0 : urlhash.length()))), sb.webIndex.size()); 
                final StringBuffer result = new StringBuffer("Sequential List of URL-Hashes:<br />");
                indexURLReference entry;
                int i = 0;
                int rows = 0, cols = 0;
                prop.put("urlhashsimilar", "1");
                while (entryIt.hasNext() && i < 256) {
                    entry = entryIt.next();
                    if (entry == null) break;
                    prop.put("urlhashsimilar_rows_"+rows+"_cols_"+cols+"_urlHash", entry.hash());
                    cols++;
                    if (cols==8) {
                        prop.put("urlhashsimilar_rows_"+rows+"_cols", cols);
                        cols = 0;
                        rows++;
                    }
                    i++;
                }
                prop.put("statistics", 0);
                prop.put("urlhashsimilar_rows", rows);
                prop.put("result", result.toString());
            } catch (final IOException e) {
                prop.put("result", "No Entries for URL hash " + urlhash);
            }
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }
        
        if (post.containsKey("lurlexport")) {
            // parse format
            int format = 0;
            final String fname = post.get("format", "url-text");
            final boolean dom = fname.startsWith("dom"); // if dom== false complete urls are exported, othervise only the domain
            if (fname.endsWith("text")) format = 0;
            if (fname.endsWith("html")) format = 1;
            if (fname.endsWith("rss")) format = 2;
            
            // extend export file name
			String s = post.get("exportfile", "");
			if (s.indexOf('.') < 0) {
				if (format == 0) s = s + ".txt";
				if (format == 1) s = s + ".html";
				if (format == 2) s = s + ".xml";
			}
        	final File f = new File(s);
			f.getParentFile().mkdirs();
			final String filter = post.get("exportfilter", ".*");
			final indexRepositoryReference.Export running = sb.webIndex.exportURL(f, filter, format, dom);

			prop.put("lurlexport_exportfile", s);
			prop.put("lurlexport_urlcount", running.count());
			if ((running != null) && (running.failed() == null)) {
				prop.put("lurlexport", 2);			    
			}
			prop.put("reload", 1);
        }
        
        if (post.containsKey("deletedomain")) {
            String hp = post.get("hashpart");
            try {
                sb.webIndex.deleteDomain(hp);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            // trigger the loading of the table
            post.put("statistics", "");
            prop.put("reload", 0);
        }
        
        if (post.containsKey("statistics")) {
            int count = post.getInt("lines", 100);
            Iterator<indexRepositoryReference.hostStat> statsiter;
            prop.put("statistics_lines", count);
            int cnt = 0;
            try {
                statsiter = sb.webIndex.statistics(count);
                boolean dark = true;
                indexRepositoryReference.hostStat hs;
                while (statsiter.hasNext() && cnt < count) {
                    hs = statsiter.next();
                    prop.put("statisticslines_domains_" + cnt + "_dark", (dark) ? "1" : "0");
                    prop.put("statisticslines_domains_" + cnt + "_domain", hs.hostname + ((hs.port == 80) ? "" : ":" + hs.port));
                    prop.put("statisticslines_domains_" + cnt + "lines", count);
                    prop.put("statisticslines_domains_" + cnt + "_hashpart", hs.hosthash);
                    prop.put("statisticslines_domains_" + cnt + "_count", hs.count);
                    dark = !dark;
                    cnt++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            prop.put("statisticslines_domains", cnt);
            prop.put("statisticslines", 1);
            prop.put("lurlexport", 0);
            prop.put("reload", 0);
        }
        
        // insert constants
        prop.putNum("ucount", sb.webIndex.countURL());
        // return rewrite properties
        return prop;
    }
    
    private static serverObjects genUrlProfile(final plasmaSwitchboard switchboard, final indexURLReference entry, final String urlhash) {
        final serverObjects prop = new serverObjects();
        if (entry == null) {
            prop.put("genUrlProfile", "1");
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        final indexURLReference.Components comp = entry.comp();
        final indexURLReference le = ((entry.referrerHash() == null) || (entry.referrerHash().length() != yacySeedDB.commonHashLength)) ? null : switchboard.webIndex.getURL(entry.referrerHash(), null, 0);
        if (comp.url() == null) {
            prop.put("genUrlProfile", "1");
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        prop.put("genUrlProfile", "2");
        prop.putHTML("genUrlProfile_urlNormalform", comp.url().toNormalform(false, true));
        prop.put("genUrlProfile_urlhash", urlhash);
        prop.put("genUrlProfile_urlDescr", comp.dc_title());
        prop.put("genUrlProfile_moddate", entry.moddate().toString());
        prop.put("genUrlProfile_loaddate", entry.loaddate().toString());
        prop.put("genUrlProfile_referrer", (le == null) ? 0 : 1);
        prop.putHTML("genUrlProfile_referrer_url", (le == null) ? "<unknown>" : le.comp().url().toNormalform(false, true));
        prop.put("genUrlProfile_referrer_hash", (le == null) ? "" : le.hash());
        prop.put("genUrlProfile_doctype", ""+entry.doctype());
        prop.put("genUrlProfile_language", entry.language());
        prop.put("genUrlProfile_size", entry.size());
        prop.put("genUrlProfile_wordCount", entry.wordCount());
        return prop;
    }

}
