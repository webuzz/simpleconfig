function isPlainObject(o, prefix, ignoringProps) {
	if (o == null) return true;
	var fieldCount = 0;
	var strLength = prefix == null ? 0 : (prefix.length + 1);
	for (var p in o) {
		if (!isAField(p, 1, ignoringProps)) continue;
		var op = o[p];
		if (op == null) continue;
		var opType = typeof op;
		if (opType != "string" && opType != "number" && opType != "boolean") {
			return false;
		}
		if (opType == "string") {
			strLength += p.length + 1 + op.length; 
			if (strLength > 100
					|| op.indexOf(">") != -1 || op.indexOf(";") != -1
					|| op.indexOf("\n") != -1 || op.indexOf("\r") != -1
					|| op.charAt(0) == '[' && op.charAt(op.length - 1) == ']') {
				return false;
			}
		}
		fieldCount++;
	}
	if (fieldCount > 8) return false;
	return true;
}

function isPlainArray(o, prefix) {
	if (o == null) return true;
	var length = o.length;
	if (length > 15) return false;
	var strLength = prefix == null ? 0 : (prefix.length + 1);
	for (var i = 0; i < length; i++) {
		var op = o[i];
		if (op == null) continue;
		var opType = typeof op;
		if (opType != "string" && opType != "number" && opType != "boolean") {
			return false;
		}
		if (opType == "string") {
			strLength += op.length + 1; 
			if (strLength > 100
					|| op.indexOf(">") != -1 || op.indexOf(";") != -1
					|| op.indexOf("\n") != -1 || op.indexOf("\r") != -1
					|| op.charAt(0) == '[' && op.charAt(op.length - 1) == ']') {
				return false;
			}
		}
	}
	return true;
}

function isAField(p, offset, ignoringProps) {
	if (ignoringProps == null) return true;
	for (var i = offset; i < ignoringProps.length; i++) {
		if (ignoringProps[i] == p) return false;
	}
	return true;
}

function visit(builder, ignoringProps, prefix, o) {
	if (o == null) {
		builder[builder.length] = prefix + "=[null]";
		return; 
	}
	if (typeof o == "string") {
		builder[builder.length] = prefix + "=" + ((o == "") ? "[empty]" : o);
		return; 
	}
	if (typeof o == "number" || typeof o == "boolean") {
		builder[builder.length] = prefix + "=" + o;
		return; 
	}
	var offset = 1;
	var oClass = o["class"];
	if (oClass != null && oClass.indexOf("[") == 0) {
		//builder[builder.length] = "# # " + oClass;
		if (oClass.indexOf("[array") == 0 || oClass.indexOf("[list") == 0 || oClass.indexOf("[set") == 0) {
			o = o["value"];
			if (o == null) {
				builder[builder.length] = prefix + "=[null]";
				return; 
			}
		} else {
			offset = 0; // ignoring the first "class" property
		}
	} else {
		oClass = null;
	}
	// Convert array to key-value pairs
	if (o instanceof Array) {
		var length = o.length;
		if (length == 0) {
			builder[builder.length] = prefix + "=[empty]";
		} else if (oClass == null && isPlainArray(o, prefix)) {
			var objBuilder = [];
			for (var i = 0; i < length; i++) {
				var pv = o[i];
				if (pv == null) {
					pv = "[null]";
				} else if (typeof pv == "string" && pv == "") {
					pv = "[empty]";
				}
				objBuilder[objBuilder.length] = pv;
			}
			builder[builder.length] = prefix + "=" + (objBuilder.length == 0 ? "[empty]" : objBuilder.join(";"));
		} else {
			builder[builder.length] = prefix + (oClass == null ? "=[]" : ("=" + oClass));
			var maxZeros = ("" + length).length;
			for (var i = 0; i < length; i++) {
				var index = "" + i;
				var leadingZeros = maxZeros - index.length;
				for (var j = 0; j < leadingZeros; j++) {
					index = "0" + index;
				}
				visit(builder, ignoringProps, prefix + "." + index, o[i]);
			}
		}
		return;
	}
	// Convert normal object or map to key-value pairs
	if (oClass == null && isPlainObject(o, prefix, ignoringProps)) {
		var objBuilder = [];
		var fields = 0;
		var type = null;
		var value = null;
		for (var p in o) {
			if (!isAField(p, offset, ignoringProps)) continue;
			var pv = o[p];
			if (pv == null) {
				pv = "[null]";
			} else if (typeof pv == "string" && pv == "") {
				pv = "[empty]";
			}
			objBuilder[objBuilder.length] = p + ">" + pv;
			fields++;
			if (fields == 1) {
				type = p;
				value = pv;
			}
		}
		if (fields == 1) {
			if ("Integer" == type || "Long" == type
					|| "Byte" == type || "Short" == type
					|| "Double" == type || "Float" == type
					|| "String" == type || "Class" == type
					|| "BigDecimal" == type || "BigInteger" == type
					|| "Boolean" == type || "Character" == type
					|| "Enum" == type) {
				builder[builder.length] = prefix + "=[" + type + ":" + value + "]";
				return;
			}
			if (typeof $configurationCodecs != null) {
				for (var i = 0; i < $configurationCodecs.length; i++) {
					if ($configurationCodecs[i] == type) {
						builder[builder.length] = prefix + "=[" + type + ":" + value + "]";
						return;
					}
				}
			}
		}
		builder[builder.length] = prefix + "=" + (objBuilder.length == 0 ? "[empty]" : objBuilder.join(";"));
	} else {
		var generated = false;
		for (var p in o) {
			if (!isAField(p, offset, ignoringProps)) continue;
			if (prefix == null) {
				visit(builder, ignoringProps, p, o[p]);
			} else {
				if (!generated) {
					builder[builder.length] = prefix + (oClass == null ? "=[]" : ("=" + oClass));
				}
				visit(builder, ignoringProps, prefix + "." + p, o[p]);
			}
			generated = true;
		}
		if (!generated) {
			builder[builder.length] = prefix + "=[empty]";
		}
	}
}

function convertToProperties(configObj) {
	var ignoringProps = [ "class" ];
	var emptyObject = {};
	for (var p in emptyObject) {
		ignoringProps[baseProps.length] = p;
	}
	// All properties in an empty object will be ignored
	
	var configProps = [];
	visit(configProps, ignoringProps, null, configObj);
	for (var i = 0; i < configProps.length; i++) {
		var line = configProps[i];
		line = line.replace(new RegExp("\n", "gm"), "\\n");
		line = line.replace(new RegExp("\r", "gm"), "\\r");
		configProps[i] = line;
	}
	return configProps.join("\r\n");
}
