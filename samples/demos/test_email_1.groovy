import edu.uvm.banner.ScriptTool;
@groovy.transform.BaseScript ScriptTool scriptTool


email([to:'mlm@uvm.edu', subject:'Subject test', from: 'joe.social@uvm.edu'
	, body: "Hi there,\nHope all is well.\n cheers."
	,attachments: ['sampledata/test.txt','sampledata/Receipt.pdf'] ]).send()
