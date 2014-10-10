package de.qabel.core.crypto;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;

import javax.xml.bind.DatatypeConverter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class QblKeyFactoryTest {

	private byte[] toByteArray(String s) {
		return DatatypeConverter.parseHexBinary(s);
	}

	@Test
	public void QblPrimaryKeyPairTest() {
		assertNotNull(QblKeyFactory.getInstance().generateQblPrimaryKeyPair());
	}

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void recreatePrimaryKeyPairTest() throws InvalidKeySpecException {

		final BigInteger primaryModulus = new BigInteger(
				"20403374758096288702659968867161536257570095862265928794315785922096668931507167941767863950462823076605179128538533500740117482834366233133461468004484731528955251076464842579740458678832072642633690333550825947218082035757371826220857972170348335961783652476060902526493622751115128783552157467661863712349327753986261809489989525869928584609573759595122868862583292170270712514564027292750536287372205388610140095762600299827665796264223783991963202837142731621933382937433531690681501504473207745366107372327123673458778821996021003868568196470606717101666674720211093779903059014275309583090084324727524587175031");
		final BigInteger primaryPrivateExponent = new BigInteger(
				"20042547876416724508290636979570706336723494078474082791712493188469709514817102671088568982451067702605981688772047999002811897247521695479896583413838260011460414022623153876413800583271299763301214829689108027984278885179182498870048896537569390917309427943052760774075811304626238015586932472544295010049382737271001079939201696937851487396866663845235290758249086666971501641749367895702885689497196986765354292113023119235785445776897721361426824385011285461666540057537070008254149643802538146044343268996869294703231869043780016849675573414385608216289547210360736584487218665529472676185221402441059745104881");
		final BigInteger primaryPublicExponent = new BigInteger("65537");

		final BigInteger encModulus = new BigInteger(
				"21852497234632184360660948528543707181989239964422344931056132288522804603938002850833347890880191373920403565926496569501866811725241237454380507352913016992400467145406889593300851537042166709993164930583350288850327142963392255003930296724384529052285776387168228224930635880755719288038490616111060510945013116908599590702630081866093239545790942346889737792395734265747719169992203150555688129711178199255515394424140646120339169221476941050123435173582223852526493989232389486891487408943517908838884505016332012239632211449283558351407275835664342586158732973078940454041127835512403724399317350414939073435361");
		final BigInteger encPrivateExponent = new BigInteger(
				"11658310775770628713339783700077851868579150496911101639828594676837684669302658523834432086557746176934449094085082107146547312284073647349664465249969493967106976719593611055436943606529781907139494005415509704432043551376658174831887619126163551486703997522024961955177605824721042604742020322897098123268855757215008009968702658211504971938789501302190757720160690148589406817833061534529808311880908389715667376477924797745854832328340127920811000807936995092934882816473105171302328937409792394703386334540941054303734549514891086852651606256702441687804077745896790070945238730870181353058830048877872482006417");
		final BigInteger encPublicExponent = new BigInteger("65537");

		final BigInteger signModulus = new BigInteger(
				"20212638097524086438477497016165657393261964206512600031715716558335572254743040433506173551638709382539477482072169078623729669812654278241195677872106121484764527325613099220512811607430492047137357044816537185911928061520079152495994321182285977515426469466474526505567832255362773940342396878912134986055018840851162726892065470862137192875670065715685641523313077610726813960481770410034090192954636255140111948958171927136666897827700350100580662257837853689842885234847392501508048308512447945975722200001798447360295953525131945790670525919110748560001164443621599498287796401720297963530988189903158700597939");
		final BigInteger signPrivateExponent = new BigInteger(
				"17272203051339570209409269044910894776917615720239332584283309800821933457167317597656991278398807414900870613981227168014476291232709424360921030072654517623804382620753320958334665737689089310097096397162832171485202362471718155808675556848369037319322345690693393165560146326458053145261382919720406726338145969399119055430558139886664500596601270609831778814464219582429935300933414936383497666565242589081497891603162040636570226240743965582199202913838791516631540291492073541240593921244191395660336113763965286900423678375768291111181840139964607798931191071968591188886075938645490455673159512274589791503393");
		final BigInteger signPublicExponent = new BigInteger("65537");

		QblPrimaryKeyPair qpkp = QblKeyFactory.getInstance()
				.createQblPrimaryKeyPair(primaryModulus,
						primaryPrivateExponent, primaryPublicExponent);
		QblEncKeyPair qekp = QblKeyFactory.getInstance().createQblEncKeyPair(
				encModulus, encPrivateExponent, encPublicExponent);
		QblSignKeyPair qskp = QblKeyFactory.getInstance().createQblSignKeyPair(
				signModulus, signPrivateExponent, signPublicExponent);

		qpkp.attachEncKeyPair(qekp);
		qpkp.attachSignKeyPair(qskp);

		assertEquals(primaryModulus, qpkp.getRSAPrivateKey().getModulus());
		assertEquals(primaryModulus, qpkp.getQblPrimaryPublicKey().getModulus());
		assertEquals(primaryPrivateExponent, qpkp.getRSAPrivateKey()
				.getPrivateExponent());
		assertEquals(primaryPublicExponent, qpkp.getQblPrimaryPublicKey()
				.getPublicExponent());

		assertEquals(encModulus, qpkp.getEncKeyPairs().getRSAPrivateKey()
				.getModulus());
		assertEquals(encModulus, qpkp.getEncKeyPairs().getQblEncPublicKey()
				.getModulus());
		assertEquals(encPrivateExponent, qpkp.getEncKeyPairs()
				.getRSAPrivateKey().getPrivateExponent());
		assertEquals(encPublicExponent, qpkp.getEncKeyPairs()
				.getQblEncPublicKey().getPublicExponent());

		assertEquals(signModulus, qpkp.getSignKeyPairs().getRSAPrivateKey()
				.getModulus());
		assertEquals(signModulus, qpkp.getSignKeyPairs().getQblSignPublicKey()
				.getModulus());
		assertEquals(signPrivateExponent, qpkp.getSignKeyPairs()
				.getRSAPrivateKey().getPrivateExponent());
		assertEquals(signPublicExponent, qpkp.getSignKeyPairs()
				.getQblSignPublicKey().getPublicExponent());
	}

	@Test
	public void recreatePrimaryPublicKeyTest() throws InvalidKeySpecException {
		final BigInteger primaryModulus = new BigInteger(
				"20403374758096288702659968867161536257570095862265928794315785922096668931507167941767863950462823076605179128538533500740117482834366233133461468004484731528955251076464842579740458678832072642633690333550825947218082035757371826220857972170348335961783652476060902526493622751115128783552157467661863712349327753986261809489989525869928584609573759595122868862583292170270712514564027292750536287372205388610140095762600299827665796264223783991963202837142731621933382937433531690681501504473207745366107372327123673458778821996021003868568196470606717101666674720211093779903059014275309583090084324727524587175031");
		final BigInteger primaryPublicExponent = new BigInteger("65537");

		final BigInteger encModulus = new BigInteger(
				"21852497234632184360660948528543707181989239964422344931056132288522804603938002850833347890880191373920403565926496569501866811725241237454380507352913016992400467145406889593300851537042166709993164930583350288850327142963392255003930296724384529052285776387168228224930635880755719288038490616111060510945013116908599590702630081866093239545790942346889737792395734265747719169992203150555688129711178199255515394424140646120339169221476941050123435173582223852526493989232389486891487408943517908838884505016332012239632211449283558351407275835664342586158732973078940454041127835512403724399317350414939073435361");
		final BigInteger encPublicExponent = new BigInteger("65537");
		final byte[] encPrimaryKeySignature = toByteArray("0E703849AFF5B5FA1D7E4F8792CD2EDE974E0CE7E8DDA525B565D3EED563354570C90F1DB3F2083E9A4F9CC1A62E69BC583AD6B8B6DEA03CBA317C46ECEA1BBADD8D23D228126F67AD653932304DC6230FE0FF00A1EF5D8A9F24886BC0A7EFBD6C9633BB49A2BECBD4152A3F830C9A7A6A942A15322C0B3BC24F0DC57CA8E4BCD3F6ED1733BF3F090F91D371247C1FCE05B9E43AA8CFD7D730E752C2682F03C53E23BC128327241EDB923D5A3CCF682AB6BF71B4E4AC4327D402C3505A832E051CC20100BC314D8548093F7BDDCD832628E1906AFDB0AC7B8F6C01E6AFDAF77517326AEE348D0437814102DD7E2A13D7D166245D7B61183A3978BDF8BAFFA651");

		final BigInteger signModulus = new BigInteger(
				"20212638097524086438477497016165657393261964206512600031715716558335572254743040433506173551638709382539477482072169078623729669812654278241195677872106121484764527325613099220512811607430492047137357044816537185911928061520079152495994321182285977515426469466474526505567832255362773940342396878912134986055018840851162726892065470862137192875670065715685641523313077610726813960481770410034090192954636255140111948958171927136666897827700350100580662257837853689842885234847392501508048308512447945975722200001798447360295953525131945790670525919110748560001164443621599498287796401720297963530988189903158700597939");
		final BigInteger signPublicExponent = new BigInteger("65537");
		final byte[] signPrimaryKeySignature = toByteArray("97AF329E75A0D7DC7C5F5CE6C76D232A368EA4A73E6C584A9DCDF15495AEA456FC4BD0C2FCBEC5107D336970E863FD6DE1E99D84EEDC1AF88268962215B8A89B97D36D60EE80FF62517CFB7ECB16849DEE9FC0048411BAE0BC997A0AF15E1E35CFD36A47B54B734C761A8AD24F979194075DB0820ACF689A28FC745F5535BFA3A97A2BFE0A72B989ABBC2BFA52395CEE2846FCA44980F88DC34897BA4461C3ED23CD1C51138815FEB02382DF0FE368AA1EA7882799545174A32BAF7BE342D1E03DD57EAE14FD4BB23ABFD38ABC1C0C999C902EB2DDE9C401E1C44C02F751C5DE33FD5F5C4797851DB44B528C0C39DE19C3C8500EE051AA549DD4CDE886FE84B8");

		QblPrimaryPublicKey qppk = QblKeyFactory.getInstance()
				.createQblPrimaryPublicKey(primaryModulus,
						primaryPublicExponent);

		QblEncPublicKey qepk = QblKeyFactory.getInstance()
				.createQblEncPublicKey(encModulus, encPublicExponent,
						encPrimaryKeySignature);
		QblSignPublicKey qspk = QblKeyFactory.getInstance()
				.createQblSignPublicKey(signModulus, signPublicExponent,
						signPrimaryKeySignature);

		qppk.attachEncPublicKey(qepk);
		qppk.attachSignPublicKey(qspk);

		assertEquals(primaryModulus, qppk.getModulus());
		assertEquals(primaryPublicExponent, qppk.getPublicExponent());

		assertEquals(encModulus, qppk.getEncPublicKey().getModulus());
		assertEquals(encPublicExponent, qppk.getEncPublicKey()
				.getPublicExponent());
		assertEquals(encPrimaryKeySignature, qppk.getEncPublicKey()
				.getPrimaryKeySignature());

		assertEquals(signModulus, qppk.getSignPublicKey().getModulus());
		assertEquals(signPublicExponent, qppk.getSignPublicKey()
				.getPublicExponent());
		assertEquals(signPrimaryKeySignature, qppk.getSignPublicKey()
				.getPrimaryKeySignature());
	}
}
