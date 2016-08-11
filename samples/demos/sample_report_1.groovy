import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool

rpt.addHead( new Date().format('MM/dd/yyyy h:mm a') , "Sluggo and Sons",{"Page " + delegate.pgno})
.addHead("", "Nice Report Title","")
.addFoot("", {"Page " + delegate.pgno}, "")
.addColHead( 10, 'L',"%-10s", ["ID"])
.addColHead( 20, 'L',"%-20s",  ["Product"])
.addColHead( 12, 'R',"%,12.0f", ["First","Number"])
.addColHead( 12, 'R',"%,12.2f", ["Second","Number"])
.addColHead( 12, 'R',"%,12.3f", ["Third","Number"])
.addColHead( 14, 'R',"%,14.0f", "Total")

// Generate and print some pseudo random data
Random rand = new Random()
def max = 10000
def p = ["Greasy Sandwich","Clam Chowda","Ham & Eggs","Milk & Cookies","Donut","Burger and Fries",
    "Tuna Salad on Rye","Ice Cream Sundae","Philly Cheese Steak", "Italian Hoagie","Ham Grinder"]
def n1tot = 0; def n2tot = 0; def n3tot = 0;def grandtotal = 0;
for(i in 1..30){
    def n = rand.nextFloat()
    String id = "99" + String.format("%7.0f", n * 10000000)
    String name = p[rand.nextInt( p.size())]
    def n1 = rand.nextInt((Integer) max)  + rand.nextFloat()
    def n2 = rand.nextInt((Integer) max)  + rand.nextFloat()
    def n3 = rand.nextInt((Integer) max)  + rand.nextFloat()
    def tot = n1 + n2 + n3
    n1tot+= n1; n2tot+= n2; n3tot+= n3; grandtotal+= tot
    rpt.pl([id,name,n1,n2,n3,tot])
}
//print a total line and close the report.
rpt.pl(["",null, "==========", "==========", "==========", "=========="])
rpt.pl("TOTAL","", n1tot, n2tot, n3tot, grandtotal)

