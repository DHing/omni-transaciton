
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.configuration2.Configuration;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import com.alibaba.fastjson.JSON;
import com.bscoin.coldwallet.cointype.common.ConfigUtil;
import com.bscoin.coldwallet.cointype.common.UnSpentUtxo;
 
import org.bitcoinj.core.TransactionConfidence;
 
  
/**  
    * @ClassName: RawTransaction  
    * @author DHing  
    *    
*/  
public class RawTransaction {
	
	private static Logger LOG = LoggerFactory.getLogger(RawTransaction.class);
	static NetworkParameters params;
	
	static {
		try {
			Configuration config = ConfigUtil.getInstance();
			params = config.getBoolean("usdtcoin.testnet") ? TestNet3Params.get() : MainNetParams.get();
			LOG.info("=== [USDT] usdtcoin  client networkID：{} ===", params.getId());
		} catch (Exception e) {
			LOG.info("=== [USDT] com.bscoin.coldwallet.cointype.usdt.RawTransaction:{} ===", e.getMessage(), e);
		}
	}
	
	  
	  
	    /**  
	    * @Title: createRawTransaction  
	    * @param @param privBtcKey   btc私钥
	    * @param @param btcAddress  比特币地址
	    * @param @param privUsdtKey  usdt私钥
	    * @param @param recevieUsdtAddr  usdt接收地址
	    * @param @param formUsdtAddr  发送的usdt地址
	    * @param @param fee 手续费
	    * @param @param omniHex - usdt hex
 	    * @param @param unBtcUtxos - btc utxo
	    * @param @param unUsdtUtxos - usdt utxo 
	    * @param @return    参数  
	    * @return String    返回类型  
	    * @throws
	 */
	public static String createRawTransaction(String privBtcKey, String btcAddress, String privUsdtKey, String recevieUsdtAddr, String formUsdtAddr, long fee, String omniHex, List<UnSpentUtxo> unBtcUtxos, List<UnSpentUtxo> unUsdtUtxos) {
		List<UTXO> btcUtxos = new ArrayList<UTXO>();
		List<UTXO> usdtUtxos = new ArrayList<UTXO>();
		try {
			if (!unBtcUtxos.isEmpty() && !unUsdtUtxos.isEmpty()) {
					// find a btc eckey info
					DumpedPrivateKey btcPrivateKey = DumpedPrivateKey.fromBase58(params, privBtcKey);
					ECKey btcKey = btcPrivateKey.getKey();
					// a usdt eckey info
					DumpedPrivateKey usdtPrivateKey = DumpedPrivateKey.fromBase58(params, privUsdtKey);
					ECKey usdtKey = usdtPrivateKey.getKey();
					
					// receive address
					Address receiveAddress = Address.fromBase58(params, recevieUsdtAddr);
					// create a transaction
					Transaction tx = new Transaction(params);
					// odd address
					Address oddAddress = Address.fromBase58(params, btcAddress);
					// 如果需要找零 消费列表总金额 - 已经转账的金额 - 手续费
					long value_btc = unBtcUtxos.stream().mapToLong(UnSpentUtxo::getValue).sum();
					long value_usdt = unUsdtUtxos.stream().mapToLong(UnSpentUtxo::getValue).sum();
					// 总输入 - 手续费 - 546 -546 = 找零金额
					long leave = (value_btc + value_usdt) - fee - 1092;
					if (leave > 0) {
						tx.addOutput(Coin.valueOf(leave), oddAddress);
					}
					
					// usdt transaction
					tx.addOutput(Coin.valueOf(546), new Script(Utils.HEX.decode(omniHex)));
					// send to address
					tx.addOutput(Coin.valueOf(546), receiveAddress);
					
					// btc utxos is an array of inputs from my wallet
					for (UnSpentUtxo unUtxo : unBtcUtxos) {
						btcUtxos.add(new UTXO(Sha256Hash.wrap(unUtxo.getHash()), unUtxo.getTxN(), Coin.valueOf(unUtxo.getValue()), unUtxo.getHeight(), false, new Script(Utils.HEX.decode(unUtxo.getScript())), unUtxo.getAddress()));
					}
					// usdt utxos is an array of inputs from my wallet
					for (UnSpentUtxo unUtxo : unUsdtUtxos) {
						usdtUtxos.add(new UTXO(Sha256Hash.wrap(unUtxo.getHash()), unUtxo.getTxN(), Coin.valueOf(unUtxo.getValue()), unUtxo.getHeight(), false, new Script(Utils.HEX.decode(unUtxo.getScript())), unUtxo.getAddress()));
					}
					
					// create usdt utxo data
					for (UTXO utxo : usdtUtxos) {
						TransactionOutPoint outPoint = new TransactionOutPoint(params, utxo.getIndex(), utxo.getHash());
						tx.addSignedInput(outPoint, utxo.getScript(), usdtKey, Transaction.SigHash.ALL, true);
					}
					
					// create btc utxo data
					for (UTXO utxo : btcUtxos) {
						TransactionOutPoint outPoint = new TransactionOutPoint(params, utxo.getIndex(), utxo.getHash());
						tx.addSignedInput(outPoint, utxo.getScript(), btcKey, Transaction.SigHash.ALL, true);
					}
					
					Context context = new Context(params);
					tx.getConfidence().setSource(TransactionConfidence.Source.NETWORK);
					tx.setPurpose(Transaction.Purpose.USER_PAYMENT);
					
					LOG.info("=== [USDT] sign success,hash is :{} ===", tx.getHashAsString());
					return new String(Hex.encodeHex(tx.bitcoinSerialize()));
			}
		} catch (Exception e) {
			LOG.info("=== com.bscoin.coldwallet.cointype.usdt.RawTransaction.createRawTransaction(String, String, String, String, String, long, String, List<UnSpentUtxo>, List<UnSpentUtxo>):{}  ===",
						   e.getMessage(), e);
		}
		return null;
	}
	
	public static void main(String[] args) {
		Map m = new HashMap();
		List<UnSpentUtxo> us = new ArrayList<UnSpentUtxo>();
		UnSpentUtxo u = new UnSpentUtxo();
		u.setAddress("mvEtuEqYPMrLaKjJ5nTZ57vQAoYUtVmMaQ");
		u.setHash("d235e908767d4bbf579e04ae768fa16298c8ccb2dc406f1cda90341477ccbb3f");
		u.setHeight(1413239);
		u.setScript("76a914a1806613a51a81966779e2fa1537013cf4cd2b1788ac");
		u.setTxN(0);
		u.setValue(300000);
		
		UnSpentUtxo u1 = new UnSpentUtxo();
		u1.setAddress("mvEtuEqYPMrLaKjJ5nTZ57vQAoYUtVmMaQ");
		u1.setHash("d74b16fd8e548e467bd1f4ce1214037fc6087bb7bf4f15cfa684d03d1cb2eda4");
		u1.setHeight(1413334);
		u1.setScript("76a914a1806613a51a81966779e2fa1537013cf4cd2b1788ac");
		u1.setTxN(1);
		u1.setValue(300000);
		
		us.add(u);
		us.add(u1);
		
		List<UnSpentUtxo> us2 = new ArrayList<UnSpentUtxo>();
		UnSpentUtxo u3 = new UnSpentUtxo();
		u3.setAddress("moUseQWZenTkU3a2bCZydth3CUUZqNY6Fk");
		u3.setHash("bd6da7714f1eb5f36e62070bc8463f8d574b98083a0df872285d291417b3afe3");
		u3.setHeight(1413334);
		u3.setScript("76a914575c4b21030d58d02c434fc36f66a866142e74ce88ac");
		u3.setTxN(1);
		u3.setValue(546);
		us2.add(u3);
		
		m.put("btcUtxo", us);
		m.put("usdtUtxo", us2);
		m.put("omniHex", "6a146f6d6e6900000000000000010000000059682f00");
		
		System.out.println("传输参数：" + JSON.toJSONString(m));
		String c = createRawTransaction("cNRE3D1pbPPvGs9wpZd3X9NuLsuUQPzPa7ktQyF1nhqBabraocU9", "muY7H7zdguqRRdCA1CX152aBfFZ2q6RURx", "cMnUzSFXsXdeAq3RSfSTLCxB669VuXBzDC9oL2dQykPfj1P4ZxKP", "n21WGsyEXkrgEtM9ahx7e5pgQJYtagvKgo", "moUseQWZenTkU3a2bCZydth3CUUZqNY6Fk", 99454, "6a146f6d6e6900000000000000010000000059682f00", us, us2);
		System.out.println(c);
	}
}
