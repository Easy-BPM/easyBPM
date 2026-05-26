# Easy BPM Admin Dashboard: Troubleshooting Guide

## Common Issues & Solutions

### Issue 1: Dashboard Not Loading

**Symptom**: Page shows "Loading..." for more than 5 seconds, or blank screen

**Possible Causes**:
- Backend API not running
- Network connectivity issue
- Browser compatibility issue
- Large dataset taking time to load

**Solutions**:

**Solution A: Check Backend**
```
1. Open browser DevTools (F12)
2. Go to Network tab
3. Refresh page (F5)
4. Look for API request to /admin/metrics/execution
5. Check response status:
   - 200 = Success, check response data
   - 500 = Server error, check backend logs
   - 0 = Cannot connect, backend down
```

**Action If Backend Down**:
- Restart backend service: `./gradlew bootRun`
- Wait 30 seconds for startup
- Refresh dashboard

**Solution B: Clear Browser Cache**
```
1. Ctrl+Shift+Delete (Windows) or Cmd+Shift+Delete (Mac)
2. Select "Cached images and files"
3. Click "Clear data"
4. Refresh page (F5)
5. Wait for full reload
```

**Solution C: Try Different Browser**
- Chrome: Full support
- Firefox: Full support
- Safari: May require clear cache
- Edge: Full support
- Internet Explorer: Not supported

---

### Issue 2: Metrics Showing Zero or Incorrect Numbers

**Symptom**: All metrics show 0, or counts don't match expected numbers

**Possible Causes**:
- Date range filter excludes all data
- Processes not deployed
- Database connection issue
- Stale cache

**Solutions**:

**Solution A: Check Date Range**
```
1. Look at period selector: [24h] [7d] [30d] [Custom]
2. If [24h] selected:
   - Are there instances from last 24 hours?
   - Check timestamps
3. If [Custom] selected:
   - Is date range correct?
   - Click "Clear All" and use [7d] instead
4. Try [7d] to see if data appears
```

**Solution B: Verify Data Exists**
```
1. Go to Processes tab (not Incidents)
2. Check if any process names listed
3. If no processes:
   - No process definitions deployed
   - Deploy a test process first
   - Restart dashboard
4. If processes listed but metrics zero:
   - Check date range (may be outside data window)
```

**Solution C: Refresh Dashboard**
```
1. Click Refresh button (⟳) at top-right
2. Wait 1-2 seconds
3. Check if metrics update
4. If still zero, try:
   - Clear browser cache (see Issue 1, Solution B)
   - Restart backend
   - Check database connection
```

---

### Issue 3: Filters Not Working

**Symptom**: Selected filter doesn't reduce results, or results unchanged

**Possible Causes**:
- Filter not properly applied
- No matching results for filter
- Browser caching issue
- Conflicting filters

**Solutions**:

**Solution A: Clear All Filters**
```
1. Look at top of table for filter buttons
2. Find "Clear All" button
3. Click "Clear All"
4. All filters removed
5. Verify results update
6. Then apply filters one at a time to test
```

**Solution B: Check Filter Logic**
```
Filters work with AND logic (all must match):

Example: 
- Status: FAILED
- Process: InvoiceProcessing
- Period: 24h
Result: Only FAILED InvoiceProcessing instances in last 24h

If no results:
- No instances matching ALL criteria
- Try removing one filter at a time
```

**Solution C: Test Individual Filters**
```
1. Start with no filters (click "Clear All")
2. Add one filter: Status = Failed
3. Verify results change
4. Add second filter: Process = InvoiceProcessing
5. Verify results update
6. Add date filter if needed

If one filter works but not another:
- That filter may have no matching data
```

---

### Issue 4: Dashboard Slow or Unresponsive

**Symptom**: Dashboard takes long time to load, clicks slow to respond, or freezes

**Possible Causes**:
- Too many instances in result set (10,000+)
- Slow internet connection
- Browser running many tabs
- Database queries slow
- Large filter result set

**Solutions**:

**Solution A: Narrow Your Scope**
```
Current: Viewing all 10,000 instances
Action:  Apply filters to reduce result set

Try:
1. Filter by Status: Failed (maybe 100 instances)
2. Filter by Process: Specific process (reduce by 80%)
3. Filter by Date: Last 24 hours (reduce by 95%)
4. Result: ~50 instances, dashboard snappy!
```

**Solution B: Check Internet Connection**
```
1. Test speed: speedtest.net
2. If slow (< 5 Mbps):
   - Move closer to WiFi router
   - Connect via wired Ethernet
   - Check WiFi signal strength
3. If fast (> 10 Mbps):
   - Issue is not internet
   - Check next solution
```

**Solution C: Close Other Tabs/Apps**
```
1. Count open browser tabs (how many?)
2. If 50+ tabs open:
   - Close inactive tabs (memory hog)
   - Restart browser
   - Reopen dashboard
3. Close other heavy apps:
   - Slack
   - Zoom
   - Chrome/Firefox with many extensions
4. Restart browser with only dashboard
```

**Solution D: Increase Date Range (Counterintuitive!)**
```
If viewing: Period = Custom 24h
- This may include peak traffic period
- Load balancing may be strained

Try: Period = 7d
- Spreads data more evenly
- May actually load faster
- Gives broader context anyway
```

---

### Issue 5: Incidents Not Showing Up

**Symptom**: Incidents tab empty or incident count is 0

**Possible Causes**:
- No failed instances in current date range
- Incidents tab has different date filter
- Incidents require status update to appear
- Date range too narrow

**Solutions**:

**Solution A: Check Date Range**
```
1. Note current date range (period selector)
2. Click Incidents tab
3. Check if it shows same period
4. If different:
   - Incidents may use different period
   - Try expanding date range (24h → 7d)
5. Click Refresh button to sync
```

**Solution B: Verify Failed Instances Exist**
```
1. Click Processes tab
2. Look for any process with failures
3. If no failed processes:
   - No incidents to show (normal)
   - Try creating test failure
4. If failed processes exist:
   - Incidents should appear in Incidents tab
   - Check if incident status updated
```

**Solution C: Expand Time Window**
```
Current: Period = 24h (no incidents shown)
Try:
1. Click [7d] to expand time window
2. More historical incidents may appear
3. If incidents show up:
   - They're older than 24 hours
   - Adjust date range as needed
4. If still no incidents:
   - No failures in that window
```

---

### Issue 6: Performance Metrics Missing or Blank

**Symptom**: Performance tab loads but metrics/charts blank or show "No Data"

**Possible Causes**:
- No completed instances (nothing to analyze)
- Performance data not calculated yet
- Chart library issue
- Date range has no completed instances

**Solutions**:

**Solution A: Ensure Completed Instances Exist**
```
1. Go to Processes tab
2. Check if any process shows "Completed" count
3. If Completed = 0 for all processes:
   - Need instances to complete
   - Can't analyze performance with no completed instances
   - Start a test process and let it complete
   - Wait 5 minutes
   - Refresh dashboard
4. If Completed > 0:
   - Performance data should be available
   - Try Refresh button
```

**Solution B: Check Date Range**
```
Performance data only available for completed instances.

If no completed instances in current date range:
- Adjust date range (expand to 7d or 30d)
- Performance metrics appear for instances completed in range
```

**Solution C: Refresh Page**
```
1. Click Refresh button (⟳)
2. Wait 2-3 seconds for charts to render
3. If still blank:
   - Hard refresh (Ctrl+F5 or Cmd+Shift+R)
   - Wait for full page reload
   - Check if charts appear
```

---

### Issue 7: Cannot Retry Failed Instance

**Symptom**: Retry button disabled (grayed out) or click doesn't work

**Possible Causes**:
- Instance already being retried
- Process definition removed
- Incident already resolved/deleted
- Permission issue

**Solutions**:

**Solution A: Wait If Already Retrying**
```
If you just clicked Retry:
1. Wait 30 seconds
2. Dashboard may be executing retry
3. Check Activity feed for status
4. If retry succeeds, incident disappears
5. If retry fails, button becomes enabled again
```

**Solution B: Verify Process Definition Exists**
```
1. Go to Processes tab
2. Find the process name
3. If process in list:
   - Definition exists, retry should work
   - Try again
4. If process NOT in list:
   - Process definition deleted/removed
   - Cannot retry without process definition
   - Contact administrator to redeploy process
```

**Solution C: Check Incident Status**
```
1. Click Incidents tab
2. Find the incident
3. If incident in list:
   - Status should show FAILED or WAITING
   - Retry button should be available
4. If incident NOT in list:
   - Already resolved or deleted
   - Cannot retry
5. Try Refresh button to sync status
```

**Solution D: Check Permissions**
```
If you have Admin role:
- Retry should work
If you have limited role:
- May not have permission to retry
- Contact administrator for permission
- Check your role in system settings
```

---

### Issue 8: Filters Reset After Refresh

**Symptom**: Set filters, leave page, come back, filters are gone

**Cause**: Intended behavior - filters not persistent

**Solution**: Use URL bookmarks

```
How to Save Filter View:

1. Apply filters you want to save
2. Look at browser URL
3. Copy full URL including filter parameters
4. Create bookmark: Ctrl+D (Windows) or Cmd+D (Mac)
5. Name it: "Failed Invoices Last 7 Days"
6. Save bookmark
7. Use bookmark next time to restore filters
```

**Example URL**:
```
http://localhost:5173/dashboard?status=FAILED&process=InvoiceProcessing&period=7d
```

---

### Issue 9: Screen Reader Not Announcing Metrics

**Symptom**: Using screen reader, but metric values not announced clearly

**Cause**: May need ARIA labels configured

**Solution**: Use keyboard navigation

```
1. Press Tab to navigate to metric cards
2. Focus indicator shows current card
3. Screen reader announces: "2,400 total instances"
4. Use Tab to move through all metrics
5. All values announced by screen reader

If still not working:
- Check screen reader is enabled
- Try different screen reader (NVDA, JAWS)
- Check browser console for errors (F12)
```

---

### Issue 10: Mobile Dashboard Layout Broken

**Symptom**: Dashboard doesn't fit mobile screen, text cut off, buttons too small

**Cause**: Responsive design issue or viewport meta tag missing

**Solutions**:

**Solution A: Zoom Out**
```
1. Ctrl+- (minus key) to zoom out
2. Try 75% or 50% zoom level
3. More content fits on screen
4. Text still readable
```

**Solution B: Refresh Page**
```
1. Refresh page (F5)
2. Wait for responsive layout to apply
3. If still broken:
   - Rotate phone (landscape vs portrait)
   - Mobile app may have different layout
```

**Solution C: Use Landscape Mode**
```
If mobile portrait too narrow:
1. Rotate phone to landscape
2. More horizontal space available
3. Table and cards may fit better
4. May need to scroll less
```

---

## Error Messages

### Error Message: "Failed to load metrics"

**Meaning**: API call to backend failed

**Common Causes**:
- Backend not running
- Network connectivity lost
- API endpoint changed
- Authentication failed

**Fix**:
1. Check backend running: `./gradlew bootRun`
2. Check network connection
3. Check browser console (F12) for details
4. Restart backend if needed

---

### Error Message: "No data available for selected period"

**Meaning**: No instances exist in selected date range

**Common Causes**:
- Date range too narrow (no instances created then)
- Process not deployed yet
- All instances outside selected range

**Fix**:
1. Expand date range to 7d or 30d
2. Verify processes deployed
3. Start test instance if needed
4. Wait for instance to complete
5. Refresh dashboard

---

### Error Message: "Internal server error (500)"

**Meaning**: Backend error during processing

**Causes**:
- Database connection lost
- SQL query error
- Code bug

**Fix**:
1. Check backend logs for error details
2. Check database running: `docker ps | grep postgres`
3. Restart backend: Stop and run `./gradlew bootRun`
4. Restart database if needed
5. Try request again

---

## Performance Troubleshooting

### Dashboard loads slowly on first load

**Normal**: First load may take 5-10 seconds as:
- Browser downloads CSS/JavaScript
- Backend generates metric aggregations
- DOM renders

**Not Normal**: Takes > 30 seconds

**Fix**:
- Check network tab (DevTools F12) for slow requests
- Check backend response time
- Check database query performance
- Narrow date range to reduce data set

---

### Dashboard becomes slow after running awhile

**Cause**: Memory leak or accumulated data

**Fix**:
1. Hard refresh page (Ctrl+F5)
2. Close and reopen dashboard
3. Restart browser completely
4. Check browser Task Manager (Shift+Esc) for memory usage
5. If 500MB+, too many resources, restart browser

---

## Getting Help

### Logs to Check

**Browser Console** (F12 → Console):
```
- JavaScript errors (red messages)
- Network requests (Network tab)
- API responses
- Timing information
```

**Backend Logs**:
```
Backend running locally: See terminal output
Backend in Docker: docker logs [container-id]
Backend in production: Check app logs directory
```

**Database Logs** (if applicable):
```
PostgreSQL: Check PostgreSQL logs
Docker: docker logs [postgres-container-id]
```

### Information to Provide When Reporting Issues

```
1. Exact problem description
2. Steps to reproduce
3. Expected vs actual behavior
4. Browser: Chrome/Firefox/Safari/Edge version
5. Device: Desktop/Tablet/Mobile
6. Backend version (Easy BPM version)
7. Error messages from console (F12)
8. Screenshots if relevant
9. Time issue occurred (for log correlation)
10. If always occurs or intermittent
```

---

## FAQ

### Q: Can I export dashboard data?

**A**: Currently, no export feature. Use API endpoints:
- `GET /admin/metrics/execution`
- `GET /admin/metrics/processes`
- Parse JSON response, import to Excel/etc.

---

### Q: Can I schedule automated reports?

**A**: Not built-in yet. Use:
- Scheduled API calls via cron/scheduler
- Third-party reporting tools
- Export data daily, build reports manually

---

### Q: How often is data updated?

**A**: Every 30 seconds automatically, or click Refresh for immediate update.

---

### Q: Can I customize which metrics display?

**A**: Not currently. All metrics always shown. Future enhancement to come.

---

### Q: Do filters save between sessions?

**A**: No, filters reset after page refresh. Use URL bookmarks to save views.

---

### Q: Is there a mobile app?

**A**: No dedicated app. Dashboard responsive on mobile browsers.

---

## Frequently Asked Questions by Role

### For Operations Team

**Q: How do I know if processes are running normally?**
A: Check Incidents tab. If 0-2 incidents and <1% failure rate, all good.

**Q: What should I check first each morning?**
A: Click [24h] period, then check Incidents tab for overnight failures.

**Q: How do I find old data?**
A: Use Custom period selector to pick date range from the past.

---

### For Process Owners

**Q: How do I know if my process is fast enough?**
A: Click Performance tab, check Avg Time vs SLA Target. If Avg < SLA, good.

**Q: Why do some instances take longer?**
A: Check Performance → Activity Feed to identify bottleneck nodes.

**Q: How do I prevent failures?**
A: Check Incidents tab regularly. Look for patterns. Improve data quality or process design.

---

### For Administrators

**Q: How do I monitor system health?**
A: Check dashboard daily. Track incident rate, performance trends, SLA compliance.

**Q: How do I know if scaling needed?**
A: If Avg Time increasing or P95 time getting worse, may need scaling.

**Q: What backups should I take?**
A: Database backups daily. Dashboard data stored in DB, recover via database restore.

---

## Quick Troubleshooting Checklist

- [ ] Dashboard loads? (If no: Check backend running)
- [ ] Data shows? (If no: Check date range and periods)
- [ ] Filters work? (If no: Try "Clear All" then reapply)
- [ ] Metrics reasonable? (If no: Verify data exists)
- [ ] Incidents visible? (If no: Check failed instances exist)
- [ ] Performance metrics show? (If no: Verify completed instances)
- [ ] Refresh works? (Click ⟳ button)
- [ ] Browser cache cleared? (Ctrl+Shift+Delete)
- [ ] Tried different browser? (Chrome/Firefox)
- [ ] Backend restarted? (./gradlew bootRun)

If issue persists after checklist: Provide information to support team with logs attached.

---

## Next Steps

- Review [incident management](./easy-admin-dashboard-incidents.md)
- Learn [filtering techniques](./easy-admin-dashboard-filtering.md)
- Understand [performance analytics](./easy-admin-dashboard-performance.md)
- Use [accessibility features](./easy-admin-dashboard-accessibility.md)

---

**Remember**: Most issues can be resolved with a dashboard refresh or browser cache clear! 🔄
